package com.dsh.mobile.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 审批风险评估：在按工具名分级（[RiskClassifier]）的基础上，读审批对应工具调用的参数，
 * 说清楚危险在哪（「递归删除」「强制推送」……），并把明显只读的命令降为低风险，
 * 让「全部允许」能放行 `git status`、`ls` 这类命令。
 *
 * 思路参考 DSHA 的 destructive-guard（MIT），这里只保留手机审批用得上的常见情形。
 */
data class ApprovalRisk(
    val level: RiskLevel,
    /** 具体的危险点，空表示没有识别出特别的问题 */
    val findings: List<String> = emptyList(),
    /** 参数里的命令或路径，一行，供卡片和通知显示 */
    val subject: String? = null,
)

object CommandRisk {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val SHELL_TOOLS = setOf("bash", "pwsh", "powershell", "shell", "exec_command", "run_command", "cmd")
    private val PATH_TOOLS = setOf("write", "edit", "apply_patch", "delete", "remove", "move", "rename")

    private fun isShellTool(name: String) = name.lowercase().let { it in SHELL_TOOLS || it.startsWith("terminal_") }

    private fun argsObject(args: String?): JsonObject? =
        args?.takeIf { it.isNotBlank() }?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    private fun stringOf(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            when (val v = obj[key]) {
                is JsonPrimitive -> if (v.isString && v.content.isNotBlank()) return v.content
                is JsonArray -> {
                    val parts = v.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                    if (parts.isNotEmpty()) return parts.joinToString(" ")
                }
                else -> {}
            }
        }
        return null
    }

    /** 工具参数里的命令（command / cmd / script） */
    fun commandOf(args: String?): String? = argsObject(args)?.let { stringOf(it, "command", "cmd", "script") }

    private data class Rule(val pattern: Regex, val finding: String)

    private fun rule(pattern: String, finding: String) = Rule(Regex(pattern, RegexOption.IGNORE_CASE), finding)

    /** 命中任意一条即高风险；同一 finding 只报一次 */
    private val DANGEROUS = listOf(
        rule("""\brm\s+(-\w*\s+)*-\w*(r\w*f|f\w*r)\b""", "递归强制删除"),
        rule("""\brm\s+(-\w*\s+)*(-r|-R|--recursive)\b""", "递归删除"),
        rule("""\brm\s+(-\w+\s+)*(/|~|\*|/\*|\${'$'}HOME)(\s|$)""", "删除根目录、主目录或全部文件"),
        rule("""\bremove-item\b[^|;&]*-recurse""", "递归删除"),
        rule("""\b(rd|rmdir)\s+/s\b""", "递归删除"),
        rule("""\bdel\s+[^|;&]*/s\b""", "递归删除"),
        rule("""\bfind\b[^|;&]*(-delete\b|-exec\s+rm\b)""", "批量删除文件"),
        rule("""\bxargs\s+(-\S+\s+)*rm\b""", "批量删除文件"),
        rule("""\bgit\s+push\b[^|;&]*(--force\b|--force-with-lease\b|\s-f\b|\s\+\S)""", "强制推送，可能覆盖远端提交"),
        rule("""\bgit\s+reset\s+[^|;&]*--hard\b""", "丢弃未提交的改动"),
        rule("""\bgit\s+clean\s+-\w*f""", "删除未跟踪的文件"),
        rule("""\bgit\s+(checkout|restore)\s+[^|;&]*(--\s+\.|\s\.(\s|$))""", "丢弃工作区改动"),
        rule("""\bgit\s+branch\s+[^|;&]*-D\b""", "强制删除分支"),
        rule("""\bdd\b[^|;&]*\bof=""", "直接写磁盘设备"),
        rule("""\bmkfs(\.\w+)?\b""", "格式化磁盘"),
        rule("""\bformat(-volume)?\s+[a-z]:""", "格式化磁盘"),
        rule("""\bdiskpart\b""", "修改磁盘分区"),
        rule(""">\s*/dev/(sd|nvme|disk)""", "直接写磁盘设备"),
        rule("""\b(curl|wget|iwr|invoke-webrequest|irm|invoke-restmethod)\b[^;&]*\|\s*(sudo\s+)?(sh|bash|zsh|iex|invoke-expression|python3?|node)\b""", "下载后直接执行脚本"),
        rule("""\b(sudo|doas|runas)\b""", "以管理员权限运行"),
        rule("""\bchmod\s+(-\w+\s+)*(0?777|a\+rwx)\b""", "把文件权限放开给所有人"),
        rule("""\b(chown|chmod)\s+(-\w*\s+)*-R\s+[^|;&]*\s/(\s|$)""", "修改根目录权限"),
        rule("""\b(shutdown|reboot|halt|poweroff|stop-computer|restart-computer)\b""", "关机或重启"),
        rule("""\b(npm|pnpm|yarn)\s+publish\b|\bcargo\s+publish\b|\btwine\s+upload\b|\bgh\s+release\s+create\b""", "发布到外部仓库"),
        rule("""\bdrop\s+(table|database|schema)\b|\btruncate\s+table\b""", "删除数据库数据"),
        rule("""\b(kill\s+-9|pkill|killall|taskkill\s+[^|;&]*/f|stop-process\s+[^|;&]*-force)\b""", "强制结束进程"),
        rule("""\breg\s+(delete|add)\b|\bremove-itemproperty\b|\bset-itemproperty\s+[^|;&]*hklm""", "修改系统注册表"),
        rule(""":\(\)\s*\{\s*:\|:&\s*\};:""", "fork 炸弹"),
    )

    /** 只读命令：每一段都以这些命令开头时视为低风险 */
    private val READ_ONLY = setOf(
        "ls", "dir", "pwd", "cat", "type", "head", "tail", "less", "more", "wc", "echo", "grep", "rg", "egrep",
        "which", "where", "whoami", "date", "printenv", "stat", "file", "tree", "du", "df", "uname", "hostname",
        "get-childitem", "gci", "get-content", "gc", "select-string", "test-path", "get-location", "get-item",
        "resolve-path", "measure-object", "sort", "uniq", "cut", "jq", "diff", "realpath", "basename", "dirname",
        "find", "fd", "git", "true",
    )
    private val GIT_READ_ONLY = setOf(
        "status", "log", "diff", "show", "branch", "remote", "rev-parse", "ls-files", "blame", "describe",
        "shortlog", "tag", "config", "grep", "ls-tree", "cat-file", "reflog", "stash list",
    )
    private val VERSION_FLAGS = setOf("--version", "-v", "-V", "version")

    private fun segments(command: String): List<String> =
        command.split(Regex("""&&|\|\||[;|\n]""")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun isReadOnlySegment(segment: String): Boolean {
        val words = segment.split(Regex("""\s+""")).dropWhile { Regex("""^\w+=\S*$""").matches(it) }
        val head = words.firstOrNull()?.lowercase()?.removeSuffix(".exe") ?: return false
        if (words.size == 2 && words[1] in VERSION_FLAGS) return true
        if (head !in READ_ONLY) return false
        return when (head) {
            "git" -> {
                val sub = words.drop(1).firstOrNull { !it.startsWith("-") }?.lowercase() ?: return false
                val rest = words.drop(2).joinToString(" ")
                sub in GIT_READ_ONLY &&
                    !(sub == "branch" && Regex("""(^|\s)-[dDmM]\b|--delete|--move""").containsMatchIn(rest)) &&
                    !(sub == "tag" && Regex("""(^|\s)-d\b|--delete""").containsMatchIn(rest)) &&
                    !(sub == "config" && !Regex("""--get|--list|-l\b""").containsMatchIn(rest)) &&
                    !(sub == "remote" && Regex("""\b(add|remove|rm|rename|set-url)\b""").containsMatchIn(rest))
            }
            "find", "fd" -> !Regex("""-delete|-exec|-ok|--exec|-x\b""").containsMatchIn(segment)
            else -> true
        }
    }

    /** 命令里有写文件的重定向（`2>&1`、`> /dev/null`、`>$null` 除外） */
    private fun hasWriteRedirect(command: String): Boolean =
        Regex(""">>?\s*(?!&\d|/dev/null\b|\${'$'}null\b|nul\b)\S""", RegexOption.IGNORE_CASE).containsMatchIn(
            command.replace(Regex("""\d>&\d"""), ""),
        )

    /** 评估一条 shell 命令；level 为 null 表示看不出特别的危险，也不能确定是只读 */
    fun assessCommand(command: String): Pair<RiskLevel?, List<String>> {
        val findings = DANGEROUS.filter { it.pattern.containsMatchIn(command) }.map { it.finding }.distinct()
        if (findings.isNotEmpty()) return RiskLevel.HIGH to findings
        val substitution = command.contains("$(") || command.contains('`')
        if (!substitution && !hasWriteRedirect(command) && segments(command).let { s -> s.isNotEmpty() && s.all(::isReadOnlySegment) }) {
            return RiskLevel.LOW to emptyList()
        }
        return null to emptyList()
    }

    private val SENSITIVE_PATH = Regex(
        """(^|[/\\])(\.ssh|\.gnupg|\.aws|\.kube|\.git)([/\\]|$)|^/(etc|usr|bin|sbin|boot|lib|System|Library)(/|$)|^[a-z]:[/\\](windows|program files)""",
        RegexOption.IGNORE_CASE,
    )

    /** 文件类工具的目标路径 */
    fun pathOf(args: String?): String? = argsObject(args)?.let { stringOf(it, "file_path", "path", "target", "destination") }

    /** 一次审批的风险：工具名分级打底，命令/路径分析给出具体原因或降级 */
    fun assess(toolName: String, reason: String?, arguments: String?): ApprovalRisk {
        val base = RiskClassifier.level(toolName, reason)
        val name = toolName.lowercase()
        if (isShellTool(name)) {
            val command = commandOf(arguments) ?: return ApprovalRisk(base)
            val (level, findings) = assessCommand(command)
            return ApprovalRisk(level ?: base, findings, oneLine(command))
        }
        if (name in PATH_TOOLS || base == RiskLevel.MEDIUM) {
            val path = pathOf(arguments) ?: return ApprovalRisk(base)
            if (SENSITIVE_PATH.containsMatchIn(path.trim())) {
                return ApprovalRisk(RiskLevel.HIGH, listOf("改动系统目录或密钥、仓库元数据"), oneLine(path))
            }
            return ApprovalRisk(base, emptyList(), oneLine(path))
        }
        return ApprovalRisk(base)
    }

    private fun oneLine(text: String, max: Int = 200): String {
        val t = text.replace(Regex("""\s+"""), " ").trim()
        return if (t.length <= max) t else t.take(max - 1) + "…"
    }
}

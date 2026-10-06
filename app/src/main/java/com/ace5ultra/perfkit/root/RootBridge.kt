package com.ace5ultra.perfkit.root

import com.topjohnwu.superuser.Shell

/**
 * Thin wrapper around libsu (topjohnwu/libsu). All access to the root module goes
 * through this channel. Never assume root: every call degrades gracefully.
 *
 * Compatible with SukiSU Ultra / BakaSU / KernelSU style `su` binaries because
 * libsu just runs `su -c <cmd>` and talks to whatever root manager granted us.
 */
object RootBridge {

    /** Outcome of one shell command. */
    data class Result(
        val stdout: String,
        val stderr: String,
        val code: Int,
        val ok: Boolean,
    )

    @Volatile
    private var cachedShell: Shell? = null

    /**
     * Returns true when a root shell is available and responsive.
     * Never throws.
     */
    @Synchronized
    fun hasRoot(): Boolean {
        return try {
            val shell = obtain() ?: return false
            val r = shell.newJob().add("echo __perfkit_root_ok__").exec()
            r.code == 0 && r.out.any { it.contains("__perfkit_root_ok__") }
        } catch (t: Throwable) {
            false
        }
    }

    @Synchronized
    private fun obtain(): Shell? {
        cachedShell?.let { if (it.isAlive) return it }
        return try {
            val shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .build("su")
            cachedShell = shell
            shell
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Run a command through the root shell. Returns a [Result] with the merged
     * stdout, stderr and exit code. On any failure, ok=false and code=-1.
     */
    fun exec(command: String): Result {
        val shell = obtain() ?: return Result("", "no root shell", -1, false)
        return try {
            val r = shell.newJob().add(command).exec()
            Result(
                stdout = r.out.joinToString("\n").trim(),
                stderr = r.err.joinToString("\n").trim(),
                code = r.code,
                ok = r.code == 0,
            )
        } catch (t: Throwable) {
            Result("", t.message ?: "exec failed", -1, false)
        }
    }

    /** Run several commands in one job, returning the merged stdout. */
    fun exec(vararg commands: String): Result {
        val shell = obtain() ?: return Result("", "no root shell", -1, false)
        return try {
            val job = shell.newJob()
            commands.forEach { job.add(it) }
            val r = job.exec()
            Result(
                stdout = r.out.joinToString("\n").trim(),
                stderr = r.err.joinToString("\n").trim(),
                code = r.code,
                ok = r.code == 0,
            )
        } catch (t: Throwable) {
            Result("", t.message ?: "exec failed", -1, false)
        }
    }
}

package com.ace5ultra.perfkit.root

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Root channel for PerfKit. It talks to the root-side controller
 * (/data/adb/modules/ace5ultra_perfkit/bin/perfctl) through the device's `su`.
 *
 * SukiSU Ultra / BakaSU / KernelSU all provide a standard `su`, so each command is
 * run as `su -c <command>` and its stdout/stderr are captured. This direct channel
 * is used instead of a long-lived interactive shell because on some manager builds
 * the persistent session executes commands but never routes output back; `su -c`
 * is reliable everywhere and never assumes root.
 */
object RootBridge {

    private const val TAG = "PerfKitRoot"
    private const val TIMEOUT_SECONDS = 30L

    /** Outcome of one shell command. */
    data class Result(
        val stdout: String,
        val stderr: String,
        val code: Int,
        val ok: Boolean,
    )

    /** Returns true only when `su` actually yields uid 0. Never throws. */
    fun hasRoot(): Boolean {
        return try {
            val r = exec("id")
            val good = r.code == 0 && r.stdout.contains("uid=0")
            Log.e(TAG, "hasRoot -> $good (${r.stdout})")
            good
        } catch (t: Throwable) {
            Log.e(TAG, "hasRoot threw ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /** Run one command through `su -c`. */
    fun exec(command: String): Result = runSu(listOf("-c", command))

    /** Run several commands in one `su -c` invocation, returning merged stdout. */
    fun exec(vararg commands: String): Result =
        runSu(listOf("-c", commands.joinToString("\n")))

    private fun runSu(args: List<String>): Result {
        val argv = mutableListOf("su").apply { addAll(args) }
        return try {
            val pb = ProcessBuilder(argv)
            val proc = pb.start()

            val outBuf = StringBuilder()
            val errBuf = StringBuilder()
            val tOut = readerThread(proc.inputStream, outBuf)
            val tErr = readerThread(proc.errorStream, errBuf)

            val finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                tOut.join(1000); tErr.join(1000)
                return Result(outBuf.toString().trim(),
                    errBuf.toString().trim().ifEmpty { "su timed out" }, -1, false)
            }
            tOut.join(2000); tErr.join(2000)
            val code = proc.exitValue()
            Result(
                stdout = outBuf.toString().trim(),
                stderr = errBuf.toString().trim(),
                code = code,
                ok = code == 0,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "runSu failed ${t.javaClass.simpleName}: ${t.message}", t)
            Result("", t.message ?: "su failed", -1, false)
        }
    }

    private fun readerThread(stream: java.io.InputStream, sink: StringBuilder) =
        thread(isDaemon = true) {
            try {
                stream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(sink) {
                            if (sink.isNotEmpty()) sink.append('\n')
                            sink.append(line)
                        }
                    }
                }
            } catch (ignored: Throwable) {
                // stream closed when the command exits; nothing actionable
            }
        }
}

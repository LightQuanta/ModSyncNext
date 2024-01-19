package tech.lq0.modSyncNext

import kotlinx.coroutines.runBlocking


fun main() = runBlocking {
    // TODO 修改为正确读取配置文件并执行对应操作
    val version = globalConfig.minecraft.version

    syncMod(version)
}
package mod.lucky.bedrock

import mod.lucky.common.GameType
import mod.lucky.common.BlockPos
import mod.lucky.common.Vec3d
import mod.lucky.common.drop.*
import mod.lucky.common.attribute.*
import mod.lucky.common.gameAPI
import mod.lucky.common.LuckyRegistry
import mod.lucky.common.LuckyBlockSettings
import mod.lucky.bedrock.common.registerBedrockTemplateVars
import kotlin.js.JSON.stringify
import kotlin.Error

class UnparsedModConfig(
    val luck: Int,
    val drops: String,
    val doDropsOnCreativeMode: Boolean = false,
    val structures: dynamic,
)

data class LuckyBlockConfig(
    val dropContainer: DropContainer,
    val doDropsOnCreativeMode: Boolean,
)

object BedrockLuckyRegistry {
    val blockLuck: MutableMap<String, Int> = HashMap()
}

fun registerModConfig(blockId: String, unparsedConfig: UnparsedModConfig) {
    LuckyRegistry.drops[blockId] = dropsFromStrList(splitLines(unparsedConfig.drops.split('\n')))
    LuckyRegistry.blockSettings[blockId] = LuckyBlockSettings(doDropsOnCreativeMode=unparsedConfig.doDropsOnCreativeMode)
    BedrockLuckyRegistry.blockLuck[blockId] = unparsedConfig.luck

    if (unparsedConfig.structures != null) {
        for (k in js("Object").keys(unparsedConfig.structures)) {
            val unparsedStruct: String = unparsedConfig.structures[k]
            val (props, drops) = readLuckyStructure(unparsedStruct.split('\n'))
            val structureId = "$blockId:$k"
            LuckyRegistry.structureProps[structureId] = props
            LuckyRegistry.structureDrops[structureId] = drops
        }
    }
}

fun onPlayerDestroyedLuckyBlock(world: MCWorld, player: MCPlayer, pos: BlockPos, blockId: String) {
    try {
        BedrockGameAPI.logInfo("Starting drop")
        val vecPos = Vec3d(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)

        val blockEntityDropContainer = BedrockGameAPI.readAndDestroyLuckyBlockEntity(world, pos)

        // run a randrom drop
        val context = DropContext(world = world, pos = vecPos, player = player, sourceId = blockId)

        runRandomDrop(
            customDrops = blockEntityDropContainer?.drops,
            luck = blockEntityDropContainer?.luck ?: BedrockLuckyRegistry.blockLuck[blockId] ?: 0,
            context,
            showOutput = true
        )
    } catch (e: Exception) {
        BedrockGameAPI.logError("Error performing Lucky Block function", e)
    }
}

fun initServer(server: MCServer, serverSystem: MCServerSystem) {
    gameAPI = BedrockGameAPI
    registerGameDependentTemplateVars(GameType.BEDROCK)
    registerBedrockTemplateVars()

    BedrockGameAPI.initServer(server, serverSystem)

    serverSystem.initialize = {
        // turn on logging of information, warnings, and errors
        val scriptLoggerConfig = serverSystem.createEventData<MCLoggerConfigEvent>("minecraft:script_logger_config");
        scriptLoggerConfig.data.log_errors = true
        scriptLoggerConfig.data.log_information = true
        scriptLoggerConfig.data.log_warnings = true
        serverSystem.broadcastEvent("minecraft:script_logger_config", scriptLoggerConfig);
    }

    serverSystem.log = { msg ->
        val chatEvent = serverSystem.createEventData<MCChatEvent>("minecraft:display_chat_event");
        chatEvent.data.message = stringify(msg)
        serverSystem.broadcastEvent("minecraft:display_chat_event", chatEvent);
    }

    serverSystem.registerComponent("lucky:all_lucky_block_entity_data", mapOf(
        "" to UnparsedDropContainer(
            drops = emptyArray(),
            luck = 0,
        )
    ))

    // (optimization) parse all drops for the default block
    registerModConfig("lucky:lucky_block", serverSystem.createEventData<UnparsedModConfig>("lucky:lucky_block_config").data)

    serverSystem.listenForEvent<MCPlayerDestroyedBlockEvent>("minecraft:player_placed_block") { eventWrapper ->
        /*
        BedrockGameAPI.logInfo("Block interacted")
        val event = eventWrapper.data
        val pos = toBlockPos(event.block_position)
        val world = serverSystem.getComponent<MCTickWorldComponent>(event.player, "minecraft:tick_world")!!.data

        val block = serverSystem.getBlock(world.ticking_area, toMCBlockPos(BlockPos(pos.x, pos.y + 1, pos.z)))
        serverSystem.log(block)

        val container = serverSystem.getComponent<Any>(block, "minecraft:container")
        serverSystem.log(container)
        */
    }


    serverSystem.listenForEvent<MCPlayerDestroyedBlockEvent>("minecraft:player_destroyed_block") { eventWrapper ->
        BedrockGameAPI.logInfo("Received event")
        val event = eventWrapper.data
        val blockId = event.block_identifier
        if (blockId.startsWith("lucky:")) {
            LuckyRegistry.drops.getOrElse(blockId) {
                val unparsedModConfig = serverSystem.createEventData<UnparsedModConfig?>("${blockId}_config").data

                if (unparsedModConfig == null) {
                    BedrockGameAPI.logError("Lucky Block addon '${blockId}' is not configured. Make sure to call createEventData(\"${blockId}_config\", ...) at the start of your serverScript.js.")
                    null
                } else {
                    registerModConfig(blockId, unparsedModConfig)
                }
            }

            val world = serverSystem.getComponent<MCTickWorldComponent>(event.player, "minecraft:tick_world")!!.data

            onPlayerDestroyedLuckyBlock(
                world = world,
                player = event.player,
                pos = toBlockPos(event.block_position),
                blockId = event.block_identifier,
            )
            serverSystem.log("finished event!")
        }
    }
}

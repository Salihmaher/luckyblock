package mod.lucky.forge.game

import mod.lucky.common.DEFAULT_RANDOM
import mod.lucky.forge.*
import mod.lucky.java.*
import mod.lucky.java.game.LuckyItemStackData
import mod.lucky.java.game.LuckyItemValues
import mod.lucky.java.game.ThrownLuckyPotionData
import mod.lucky.java.game.readFromTag
import net.minecraft.core.NonNullList
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.TooltipFlag

class LuckyPotion : MCItem(Properties()) {

    override fun use(world: MCWorld, user: MCPlayerEntity, hand: InteractionHand): InteractionResultHolder<MCItemStack> {
        val stack = user.getItemInHand(hand)

        world.playSound(
            null as MCPlayerEntity?,
            user.x, user.y, user.z,
            SoundEvents.SPLASH_POTION_THROW,
            SoundSource.PLAYERS,
            0.5f,
            0.4f / (DEFAULT_RANDOM.nextDouble().toFloat() * 0.4f + 0.8f)
        )
        if (!isClientWorld(world)) {
            val stackData = stack.tag?.let { LuckyItemStackData.readFromTag(it) } ?: LuckyItemStackData()
            val potionEntity = ThrownLuckyPotion(
                world = world,
                user = user,
                data = ThrownLuckyPotionData(
                    customDrops = stackData.customDrops,
                    luck = stackData.luck,
                    sourceId = JAVA_GAME_API.getItemId(stack.item) ?: JavaLuckyRegistry.potionId,
                )
            )
            potionEntity.item = stack
            potionEntity.shootFromRotation(user, user.xRot, user.yRot, -20.0f, 0.5f, 1.0f)
            world.addFreshEntity(potionEntity)
        }

        user.awardStat(Stats.ITEM_USED.get(this))
        if (!user.abilities.instabuild) stack.shrink(1)

        return InteractionResultHolder.sidedSuccess(stack, isClientWorld(world))
    }

    @OnlyInClient
    override fun isFoil(stack: MCItemStack): Boolean {
        return true
    }

    @OnlyInClient
    override fun appendHoverText(stack: MCItemStack, world: MCWorld?, tooltip: MutableList<MCChatComponent>, context: TooltipFlag) {
        tooltip.addAll(createLuckyTooltip(stack))
    }
}

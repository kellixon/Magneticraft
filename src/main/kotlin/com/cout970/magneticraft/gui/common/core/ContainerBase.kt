package com.cout970.magneticraft.gui.common.core

import com.cout970.magneticraft.IVector2
import com.cout970.magneticraft.Magneticraft
import com.cout970.magneticraft.misc.inventory.InventoryRegion
import com.cout970.magneticraft.misc.network.IBD
import com.cout970.magneticraft.network.MessageContainerUpdate
import com.cout970.magneticraft.network.MessageGuiUpdate
import com.cout970.magneticraft.tileentity.core.TileBase
import com.cout970.magneticraft.util.vector.Vec2d
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.inventory.Container
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

abstract class ContainerBase(val player: EntityPlayer, val world: World, val pos: BlockPos) : Container() {

    //the TileEntity that has the gui, can be null
    val tileEntity = world.getTileEntity(pos)
    val inventoryRegions = mutableListOf<InventoryRegion>()

    override fun canInteractWith(playerIn: EntityPlayer?): Boolean = true

    fun bindPlayerInventory(playerInventory: InventoryPlayer, offset: IVector2 = Vec2d.ZERO) {

        val startIndex = inventorySlots.size
        for (i in 0..2) {
            for (j in 0..8) {
                this.addSlotToContainer(Slot(playerInventory, j + i * 9 + 9,
                    8 + j * 18 + offset.xi,
                    84 + i * 18 + offset.yi))
            }
        }

        val hotBarIndex = inventorySlots.size
        for (i in 0..8) {
            this.addSlotToContainer(Slot(playerInventory, i,
                8 + i * 18 + offset.xi,
                142 + offset.yi))
        }
        inventoryRegions += InventoryRegion(startIndex..startIndex + 26)
        inventoryRegions += InventoryRegion(hotBarIndex..hotBarIndex + 8)
    }

    override fun detectAndSendChanges() {
        super.detectAndSendChanges()
        if (player is EntityPlayerMP) {
            val ibd = sendDataToClient()
            if (ibd != null) {
                Magneticraft.network.sendTo(MessageContainerUpdate(ibd), player)
            }
        } else if (player is EntityPlayerSP) {
            val ibd = sendDataToServer()
            if (ibd != null) {
                Magneticraft.network.sendToServer(MessageGuiUpdate(ibd, player.persistentID))
            }
        }
    }

    override fun transferStackInSlot(playerIn: EntityPlayer?, index: Int): ItemStack {
        if (index in 0 until this.inventorySlots.size) {
            val slot = this.inventorySlots[index]
            // ignore empty slot
            if (!slot.hasStack)
                return ItemStack.EMPTY

            // get all regions except the slot origin
            val slotRanges = inventoryRegions.filter { index !in it.region }
            val stack = slot.stack

            //try insert in any valid range
            if (tryMergeItemStack(stack, index, slotRanges)) {
                if (stack.count == 0) {
                    slot.putStack(ItemStack.EMPTY)
                } else {
                    slot.onSlotChanged()
                }
            }
        }
        return ItemStack.EMPTY
    }

    /**
     * Try to merge the [stack] in any region in [regions]
     */
    protected fun tryMergeItemStack(stack: ItemStack, index: Int, regions: List<InventoryRegion>): Boolean {
        regions.forEach {
            if (it.advFilter(stack, index) && mergeItemStack(stack, it.region.start,
                    it.region.endInclusive + 1, it.inverseDirection)) {
                return true
            }
        }
        return false
    }

    //Called every tick to get the changes in the server that need to be sent to the client
    open fun sendDataToClient(): IBD? {
        return (tileEntity as? TileBase)?.let {
            val vars = it.container.modules.flatMap { it.getGuiSyncVariables() }
            if (vars.isEmpty()) {
                null
            } else {
                IBD().also { ibd ->
                    vars.forEach { it.write(ibd) }
                }
            }
        }
    }

    //Called every tick to get the changes in the client that need to be sent to the server
    //for example buttons
    open fun sendDataToServer(): IBD? = null

    //called when server data is received
    open fun receiveDataFromServer(ibd: IBD) {
        (tileEntity as? TileBase)?.let {
            val vars = it.container.modules.flatMap { it.getGuiSyncVariables() }
            vars.forEach { it.read(ibd) }
        }
    }

    //called when client data is received
    open fun receiveDataFromClient(ibd: IBD) = Unit


    fun sendUpdate(ibd: IBD) {
        Magneticraft.network.sendToServer(MessageGuiUpdate(ibd, player.persistentID))
    }
}
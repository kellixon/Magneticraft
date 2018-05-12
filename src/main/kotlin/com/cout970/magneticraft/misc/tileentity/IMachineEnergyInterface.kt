package com.cout970.magneticraft.misc.tileentity

interface IMachineEnergyInterface {

    fun getSpeed(): Double

    fun hasEnergy(amount: Double): Boolean

    fun useEnergy(amount: Double)
}
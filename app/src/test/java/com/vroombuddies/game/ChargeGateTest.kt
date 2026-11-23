package com.vroombuddies.game

import com.vroombuddies.game.content.Knock
import com.vroombuddies.game.content.Themes
import com.vroombuddies.game.sim.*
import org.junit.Assert.*
import org.junit.Test

class ChargeGateTest {
    @Test fun gatesRewardSteeringAndResetOnTheNextLap() {
        assertEquals(20, Themes.all.size)
        for (theme in Themes.all) {
            val track = TrackPath(theme.shape)
            assertEquals(theme.name, 3, track.chargeGates.size)
            val gate = track.chargeGates.first()
            val race = Race(track, theme.gravity, 3, theme.ground != null, listOf(Knock.cone),
                VehicleParams(), 0, 0, emptyList(), listOf(0), theme.shape.seed)
            val car = race.playerVehicle
            race.phase = RacePhase.RACING
            fun cross(distance: Float, lane: Float) {
                car.placeAt(track, distance, lane)
                race.update(0.0001f)
            }
            car.energy = 0f
            cross(gate.s, gate.x + gate.halfWidth + 2f)
            assertEquals(0f, car.energy, 0.01f)
            cross(gate.s, gate.x)
            assertEquals(25f, car.energy, 0.01f)
            cross(gate.s, gate.x)
            assertEquals(25f, car.energy, 0.01f)
            cross(gate.s + track.length, gate.x)
            assertEquals(50f, car.energy, 0.01f)
        }
    }
}

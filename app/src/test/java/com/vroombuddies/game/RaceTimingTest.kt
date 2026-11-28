package com.vroombuddies.game

import com.vroombuddies.game.content.Knock
import com.vroombuddies.game.content.Themes
import com.vroombuddies.game.sim.*
import org.junit.Assert.*
import org.junit.Test

class RaceTimingTest {
    private fun newRace(): Race {
        val theme = Themes.all.first()
        return Race(TrackPath(theme.shape), theme.gravity, 2, true, Knock.kinds(theme.knock),
            VehicleParams(), 0, 0, emptyList(), listOf(0), theme.shape.seed)
    }

    @Test fun delayedFrameAdvancesTheSameRaceAsSixShortFrames() {
        val slow = newRace()
        val fast = newRace()
        slow.phase = RacePhase.RACING
        fast.phase = RacePhase.RACING
        slow.update(.3f)
        repeat(6) { fast.update(.05f) }
        assertEquals(.3f, slow.raceTime, .0001f)
        assertEquals(fast.playerVehicle.progress, slow.playerVehicle.progress, .01f)
        assertEquals(fast.playerVehicle.speed, slow.playerVehicle.speed, .01f)
    }

    @Test fun countdownUsesElapsedTimeAndOnlyRacesAfterGo() {
        val race = newRace()
        race.update(3.5f)
        assertEquals(RacePhase.COUNTDOWN, race.phase)
        assertEquals(0f, race.raceTime, 0f)
        race.update(.3f)
        assertEquals(RacePhase.RACING, race.phase)
        assertEquals(.2f, race.raceTime, .0001f)
        race.update(8f)
        assertEquals(.7f, race.raceTime, .0001f)
    }
}

package com.vroombuddies.game.render

internal fun probe(name: String) = System.getProperty("vroom.probe", "").split(',').contains(name)

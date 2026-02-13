package com.spellapp.core.data

interface CharacterRepository : CharacterCrudRepository, PreparedSlotRepository,
    SessionEventRepository, FocusStateRepository

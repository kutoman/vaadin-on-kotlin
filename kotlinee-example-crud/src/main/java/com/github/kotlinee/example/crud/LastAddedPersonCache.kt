package com.github.kotlinee.example.crud

import com.github.kotlinee.example.crud.crud.Person
import com.github.kotlinee.framework.Session
import com.github.kotlinee.framework.SessionScoped
import org.slf4j.LoggerFactory
import java.io.Serializable

/**
 * A demo of a session-bound class which caches the last added person. Use the [lastAddedPersonCache] global property to retrieve instances.
 *
 * Note the private constructor, to avoid accidentally constructing this class by hand. To correctly manage instances of this class,
 * @author mvy
 */
class LastAddedPersonCache private constructor() : Serializable {
    init {
        log.warn("LastAddedPersonCache created")
    }

    /**
     * A simple placeholder where the last-created person is stored.
     */
    var lastAdded: Person? = null

    companion object {
        private val log = LoggerFactory.getLogger(LastAddedPersonCache::class.java)
    }
}

/**
 * Retrieves the session-scoped instance of this class, creating and binding it to the current session if necessary.
 *
 * WARNING: you can only read the property while holding the Vaadin UI lock!
 */
val Session.lastAddedPersonCache: LastAddedPersonCache by SessionScoped.get()

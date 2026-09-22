
package com.tiktokminimal.app

import org.junit.Assert.assertTrue
import org.junit.Test

class BasicTest {
    @Test
    fun projectHasExpectedNativeApplicationId() {
        assertTrue(BuildConfigMock.APPLICATION_ID == "com.tiktokminimal.app")
    }

    private object BuildConfigMock {
        const val APPLICATION_ID = "com.tiktokminimal.app"
    }
}

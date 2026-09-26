package com.nndwn.changelog.publish

import com.nndwn.changelog.publish.data.CiPublisher
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CiPublisherTest {

    @Test
    fun getCiEnvironmentInfo_returnsNonNullValues() {
        val info = CiPublisher.getCiEnvironmentInfo()

        assertNotNull(info)
        assertTrue(info.containsKey("COMMIT_HASH"))
        assertTrue(info.containsKey("BRANCH"))
        assertTrue(info.containsKey("BUILD_NUMBER"))
        assertTrue(info.containsKey("CI_PLATFORM"))
    }
}

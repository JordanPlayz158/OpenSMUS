package net.sf.opensmus

import org.junit.jupiter.api.AfterAll
import java.io.File
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.jvm.Throws

class TestCaseMUSServer {

	@Test
	@Throws(InterruptedException::class)
	fun testMUSServerCreation() {
		val props = MUSServerProperties()
		props.m_props.setProperty("LogDebugExtInformation", "1")

		val serverInstance = MUSServer(props)
		assertNotNull(serverInstance)

		//Thread.sleep(10000)

		serverInstance.killServer()
	}

    companion object {
        @JvmStatic
        @AfterAll
        fun cleanup() {
            val serverLogFile = File(MUSServerProperties.DEFAULT_LOGFILENAME)
            if (serverLogFile.exists()) {
                serverLogFile.delete()
            }
        }
    }
}
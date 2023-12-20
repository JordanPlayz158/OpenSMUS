/**
 *
 */
package net.sf.opensmus

import org.junit.jupiter.api.AfterEach
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import java.io.OutputStream
import kotlin.jvm.Throws

/**
 * @author jmayrbaeurl
 *
 */
class TestCaseMUSServerProperties {

	@Test
	@Throws(IOException::class)
	fun testCreationFromDefaultLocation() {
		val propsFile = File("./OpenSMUS.cfg")
		propsFile.delete()
		assertTrue(propsFile.createNewFile())

		try {
			val props = Properties()
			props["ServerOwnerName"] = "JUnit test case"

			val os: OutputStream = FileOutputStream(propsFile)
			props.store(os, "JUnit test case of OpenSMUS")
			os.close()

			val musProps = MUSServerProperties()
			assertNotNull(musProps)
			assertNotNull(musProps)
			assertEquals("JUnit test case", musProps.getProperty("ServerOwnerName"))
		}
		finally {
			propsFile.delete()
		}
	}

	@Test
	@Throws(FileNotFoundException::class, IOException::class)
	fun testCreationFromSysPropsLocation() {
		val propsFile = File("./TestOpenSMUS.cfg")
		propsFile.delete()
		assertTrue(propsFile.createNewFile())

		try {
			val props = Properties()
			props["ServerOwnerName"] = "JUnit test case"

			val os = FileOutputStream(propsFile)
			props.store(os, "JUnit test case of OpenSMUS")
			os.close()

			System.getProperties().setProperty("OpenSMUSConfigFile", "./TestOpenSMUS.cfg")
			val musProps = MUSServerProperties()
			assertNotNull(musProps)
			assertEquals("JUnit test case", musProps.getProperty("ServerOwnerName"))
		}
		finally {
			propsFile.delete()
		}
	}

	@AfterEach
	fun cleanup() {
		System.clearProperty("OpenSMUSConfigFile")
	}
}
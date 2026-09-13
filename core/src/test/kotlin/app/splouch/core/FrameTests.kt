package app.splouch.core

import app.splouch.core.wire.Frame
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrameTests {
    @Test fun `decodes the envelope`() {
        val f = Frame.decode("""{"event":"meet_live","data":{"live":true}}""")!!
        assertEquals("meet_live", f.event)
        assertEquals(true, f.data!!.jsonObject["live"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun `data may be absent, null, or a bare string`() {
        assertNull(Frame.decode("""{"event":"schedule_update"}""")!!.data)
        assertNull(Frame.decode("""{"event":"x","data":null}""")!!.data)
        assertEquals(JsonPrimitive("hi"), Frame.decode("""{"event":"output","data":"hi"}""")!!.data)
    }

    @Test fun `rejects what is not a frame`() {
        assertNull(Frame.decode("not json"))
        assertNull(Frame.decode("[1,2]"))
        assertNull(Frame.decode("""{"data":{}}"""))
        assertNull(Frame.decode("""{"event":3}"""))
    }

    @Test fun `ping encodes without a data key, join_meet with the two fields`() {
        assertEquals("""{"event":"ping"}""", Frame.ping().encode())
        assertEquals("""{"event":"join_meet","data":{"meet_id":"aBc","vid":"u-1"}}""", Frame.joinMeet("aBc", "u-1").encode())
    }
}

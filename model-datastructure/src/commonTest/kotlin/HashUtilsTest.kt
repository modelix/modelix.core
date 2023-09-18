/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.modelix.model.persistent.HashUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class HashUtilsTest {

    @Test
    fun testKnownIssue1() {
        val res = HashUtil.sha256("courses/2/Xsmvf*PD7Sfna8J23fw4VWAT-g9KiXvRfUjYgCYGkZP8")
        assertEquals("hgopc*4n9Ddc5-Di5f9i0gXA_LsGwuBN9ir-fmJTsvp4", res)
    }

    @Test
    fun testKnownIssue2() {
        val res = HashUtil.sha256("100000016/mps%3A96533389-8d4c-46f2-b150-8d89155f7fca%2F4128798754188010580/100000015/rooms/100000017,100000018,100000019/%23mpsNodeId%23=5042850610501964316,maximumCapacity=30,name=Marie,number=1.311/")
        assertEquals("wViE7*Dr2QLdMzP6YhAFA-8aKgx3USS1butvAODA22y0", res)
    }

    @Test
    fun testKnownIssue4() {
        val input = "L/10000002a/hCo9C*azyKaBGRwpGmcmCzK4zNpVvpJtdvaNh_Ai7Ljo"
        val res = HashUtil.sha256(input)
        assertEquals("CgU1o*tzhHi3xPr2AA-ucivjYQ2qx0fzC9V8ZIa9w0UM", res)
    }

    @Test
    fun unicodeString() {
        val res = HashUtil.sha256("⊣ⵊⰵ₪┨₩⛎⋪⯏⋂⇤⅐\u244F⪶⸎⡚⚅⑼➐≘⍗⚬☄⦍≧⯢⻱\u2029\u20C3⒋Ⱍ⑊⨩ⱡ⡉⩓⽄◳┸⇅⻖⸙⍹\u200B⯴⺁⎾ℤ\u2432␞⊘╻⼒")
        assertEquals("_KlSg*f0Y4i8Z8GRd672y1SoNwHPm22crM-NYrYUdIiQ", res)
    }

    @Test
    fun testSha256EmptyString() {
        val res = HashUtil.sha256("")
        assertEquals("47DEQ*pj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU", res)
    }

    @Test
    fun testSha256AsciiString() {
        val res = HashUtil.sha256("""!"#${'$'}%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~""")
        assertEquals("TlD0a*IifAn-A7RlySVGi9Xb6YeBMJ9vp7JiPUGtZH-U", res)
    }
}

package tw.yuaner.neoauth;

import org.junit.jupiter.api.Test;
import tw.yuaner.neoauth.util.TimeSpanParser;

import static org.junit.jupiter.api.Assertions.*;

public class TimeSpanParserTest {

    @Test
    public void testCountParsing() {
        assertTrue(TimeSpanParser.isCount("6"));
        assertTrue(TimeSpanParser.isCount("10"));
        assertFalse(TimeSpanParser.isCount("1d"));
        assertFalse(TimeSpanParser.isCount("help"));

        assertEquals(6, TimeSpanParser.parseCount("6", 6, 100));
        assertEquals(10, TimeSpanParser.parseCount("10", 6, 100));
        assertEquals(100, TimeSpanParser.parseCount("500", 6, 100));
        assertEquals(6, TimeSpanParser.parseCount("-1", 6, 100));

        TimeSpanParser.ParseResult res = TimeSpanParser.parse("6");
        assertTrue(res.isValid());
        assertTrue(res.isCount());
        assertEquals(6, res.getCount());
    }

    @Test
    public void testDefaultParsing() {
        TimeSpanParser.ParseResult resNull = TimeSpanParser.parse(null);
        assertTrue(resNull.isValid());
        assertTrue(resNull.isCount());
        assertEquals(6, resNull.getCount());

        TimeSpanParser.ParseResult resEmpty = TimeSpanParser.parse("   ");
        assertTrue(resEmpty.isValid());
        assertTrue(resEmpty.isCount());
        assertEquals(6, resEmpty.getCount());
    }

    @Test
    public void testDurationParsingUnits() {
        // 秒 (s)
        TimeSpanParser.ParseResult sRes = TimeSpanParser.parse("30s");
        assertTrue(sRes.isValid());
        assertFalse(sRes.isCount());
        assertEquals(30 * 1000L, sRes.getDurationMillis());

        // 分鐘 (m) vs 月份 (M / mo)
        TimeSpanParser.ParseResult mRes = TimeSpanParser.parse("3m");
        assertTrue(mRes.isValid());
        assertEquals(3 * 60 * 1000L, mRes.getDurationMillis(), "小寫 m 應解析為分鐘");

        TimeSpanParser.ParseResult capMRes = TimeSpanParser.parse("3M");
        assertTrue(capMRes.isValid());
        assertEquals(3 * 30L * 24 * 60 * 60 * 1000L, capMRes.getDurationMillis(), "大寫 M 應解析為月份");

        TimeSpanParser.ParseResult moRes = TimeSpanParser.parse("3mo");
        assertTrue(moRes.isValid());
        assertEquals(3 * 30L * 24 * 60 * 60 * 1000L, moRes.getDurationMillis(), "mo 應解析為月份");

        TimeSpanParser.ParseResult moUpperRes = TimeSpanParser.parse("2MO");
        assertTrue(moUpperRes.isValid());
        assertEquals(2 * 30L * 24 * 60 * 60 * 1000L, moUpperRes.getDurationMillis(), "MO 應解析為月份");

        // 小時 (h)
        TimeSpanParser.ParseResult hRes = TimeSpanParser.parse("1h");
        assertTrue(hRes.isValid());
        assertEquals(3600 * 1000L, hRes.getDurationMillis());

        // 天 (d)
        TimeSpanParser.ParseResult dRes = TimeSpanParser.parse("1d");
        assertTrue(dRes.isValid());
        assertEquals(86400 * 1000L, dRes.getDurationMillis());

        TimeSpanParser.ParseResult d5Res = TimeSpanParser.parse("5d");
        assertTrue(d5Res.isValid());
        assertEquals(5 * 86400 * 1000L, d5Res.getDurationMillis());

        // 週 (w)
        TimeSpanParser.ParseResult wRes = TimeSpanParser.parse("3w");
        assertTrue(wRes.isValid());
        assertEquals(3 * 7 * 86400 * 1000L, wRes.getDurationMillis());

        // 年 (y)
        TimeSpanParser.ParseResult yRes = TimeSpanParser.parse("1y");
        assertTrue(yRes.isValid());
        assertEquals(365L * 86400 * 1000L, yRes.getDurationMillis());
    }

    @Test
    public void testCompoundDurationParsing() {
        TimeSpanParser.ParseResult res = TimeSpanParser.parse("1h30m");
        assertTrue(res.isValid());
        assertEquals((3600 + 1800) * 1000L, res.getDurationMillis());

        TimeSpanParser.ParseResult resWithSpace = TimeSpanParser.parse("1d 2h");
        assertTrue(resWithSpace.isValid());
        assertEquals((86400 + 7200) * 1000L, resWithSpace.getDurationMillis());
    }

    @Test
    public void testInvalidInput() {
        TimeSpanParser.ParseResult res1 = TimeSpanParser.parse("invalid");
        assertFalse(res1.isValid());

        TimeSpanParser.ParseResult res2 = TimeSpanParser.parse("1x");
        assertFalse(res2.isValid());

        TimeSpanParser.ParseResult res3 = TimeSpanParser.parse("1h foo");
        assertFalse(res3.isValid());
    }

    @Test
    public void testFormatDuration() {
        // 中文
        assertEquals("45秒", TimeSpanParser.formatDuration(45 * 1000L, true));
        assertEquals("2分 5秒", TimeSpanParser.formatDuration(125 * 1000L, true));
        assertEquals("1小時 2分 3秒", TimeSpanParser.formatDuration((3600 + 120 + 3) * 1000L, true));
        assertEquals("2天 3小時 4分", TimeSpanParser.formatDuration((2 * 86400 + 3 * 3600 + 4 * 60) * 1000L, true));

        // 英文
        assertEquals("45s", TimeSpanParser.formatDuration(45 * 1000L, false));
        assertEquals("2m 5s", TimeSpanParser.formatDuration(125 * 1000L, false));
        assertEquals("1h 2m 3s", TimeSpanParser.formatDuration((3600 + 120 + 3) * 1000L, false));
        assertEquals("2d 3h 4m", TimeSpanParser.formatDuration((2 * 86400 + 3 * 3600 + 4 * 60) * 1000L, false));
    }
}

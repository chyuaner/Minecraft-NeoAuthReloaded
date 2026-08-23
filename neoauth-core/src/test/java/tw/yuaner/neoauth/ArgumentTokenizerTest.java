package tw.yuaner.neoauth;

import org.junit.jupiter.api.Test;
import tw.yuaner.neoauth.util.ArgumentTokenizer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ArgumentTokenizerTest {

    @Test
    public void testSimpleTokens() {
        List<String> tokens = ArgumentTokenizer.tokenize("fff@asd");
        assertEquals(1, tokens.size());
        assertEquals("fff@asd", tokens.get(0));
    }

    @Test
    public void testSpecialCharactersInUnquotedStrings() {
        List<String> tokens = ArgumentTokenizer.tokenize("p@ssw0rd!#$ 123456");
        assertEquals(2, tokens.size());
        assertEquals("p@ssw0rd!#$", tokens.get(0));
        assertEquals("123456", tokens.get(1));
    }

    @Test
    public void testQuotedStrings() {
        List<String> tokens = ArgumentTokenizer.tokenize("\"my secret pass\" \"confirm pass\"");
        assertEquals(2, tokens.size());
        assertEquals("my secret pass", tokens.get(0));
        assertEquals("confirm pass", tokens.get(1));
    }

    @Test
    public void testEmailAddress() {
        List<String> tokens = ArgumentTokenizer.tokenize("admin@yuaner.tw");
        assertEquals(1, tokens.size());
        assertEquals("admin@yuaner.tw", tokens.get(0));
    }

    @Test
    public void testThreeArgsForChangePassword() {
        List<String> tokens = ArgumentTokenizer.tokenize("old@123 new#456 new#456");
        assertEquals(3, tokens.size());
        assertEquals("old@123", tokens.get(0));
        assertEquals("new#456", tokens.get(1));
        assertEquals("new#456", tokens.get(2));
    }

    @Test
    public void testCleanArgument() {
        assertEquals("fff@asd", ArgumentTokenizer.cleanArgument("fff@asd"));
        assertEquals("fff@asd", ArgumentTokenizer.cleanArgument("\"fff@asd\""));
        assertEquals("admin@yuaner.tw", ArgumentTokenizer.cleanArgument("admin@yuaner.tw"));
        assertEquals("my secret pass", ArgumentTokenizer.cleanArgument("\"my secret pass\""));
    }
}

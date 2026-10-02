package tw.yuaner.neoauth;

import org.junit.jupiter.api.Test;
import tw.yuaner.neoauth.config.HelpManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HelpManagerTest {

    @Test
    public void testDefaultFallbacks() {
        HelpManager mgr = new HelpManager();
        assertNotNull(mgr.getTitle());
        assertNotNull(mgr.getAdminHeader());

        List<String> adminLines = mgr.getAdminHelpLines();
        assertFalse(adminLines.isEmpty());
        assertTrue(adminLines.get(0).contains("管理員指令"));
        assertTrue(adminLines.stream().anyMatch(l -> l.contains("/neoauth register")));
        assertTrue(adminLines.stream().anyMatch(l -> l.contains("/neoauth cb")));
    }

    @Test
    public void testCustomHelpLoading() {
        HelpManager mgr = new HelpManager();

        Map<String, Object> root = new HashMap<>();
        Map<String, Object> help = new HashMap<>();
        help.put("title", "&aCustom Title");
        help.put("admin_header", "&bCustom Admin Header");
        help.put("not_found", "&cCommand not found: %s");

        Map<String, Object> commands = new HashMap<>();
        Map<String, Object> customCmd = new HashMap<>();
        customCmd.put("usage", "/neoauth custom");
        customCmd.put("description", "A custom command.");
        commands.put("neoauth_custom", customCmd);

        help.put("commands", commands);
        root.put("help", help);

        mgr.loadHelp(root);

        assertEquals("§aCustom Title", mgr.getTitle());
        assertEquals("§bCustom Admin Header", mgr.getAdminHeader());

        List<String> adminLines = mgr.getAdminHelpLines();
        assertTrue(adminLines.stream().anyMatch(l -> l.contains("/neoauth custom")));

        List<String> queryLines = mgr.getCommandHelpLines("custom");
        assertTrue(queryLines.stream().anyMatch(l -> l.contains("/neoauth custom")));

        List<String> notFoundLines = mgr.getCommandHelpLines("unknown_cmd");
        assertTrue(notFoundLines.get(0).contains("Command not found: unknown_cmd"));
    }
}

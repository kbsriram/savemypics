package org.savemypics.plugin.snapfish;

import org.junit.Test;
import org.savemypics.plugin.CPlugin;
import static org.junit.Assert.*;

public class CSnapfishUserTest extends ABase
{
    @Test public void testLogin()
        throws Exception
    {
        if (!haveSecrets()) { return; }

        // check we correctly handle various combinations of
        // bad logins.
        try {
            CSnapfishUser.login(m_appcred, m_email, "bad");
            fail("Did not trap exception correctly");
        }
        catch (CPlugin.AuthorizationException ae) {
            assertEquals("INVALID_LOGIN_CREDENTIALS", ae.getMessage());
        }

        try {
            CSnapfishUser.login(m_appcred, "fudge@@@.", "bad");
            fail("Did not trap exception correctly");
        }
        catch (CPlugin.AuthorizationException ae) {
            assertEquals("INVALID_EMAIL_ADDRESS", ae.getMessage());
        }

        try {
            CSnapfishUser.login(m_appcred, "h381@fudge.com", "bad");
            fail("Did not trap exception correctly");
        }
        catch (CPlugin.AuthorizationException ae) {
            assertEquals("INVALID_LOGIN_CREDENTIALS", ae.getMessage());
        }
    }
}

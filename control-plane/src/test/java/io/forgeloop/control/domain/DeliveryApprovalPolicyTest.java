package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

class DeliveryApprovalPolicyTest {
    private FeatureRun run(boolean human, boolean merge) throws Exception {
        FeatureRun run=new FeatureRun("org","acme/app","issue-1","test","spec",5,"GENERIC","main",1);
        String json="{\"schema\":\"forgeloop.policy-snapshot/1\",\"organization\":{\"requireHumanApproval\":"+human+",\"autoMergeEnabled\":"+merge+"}}";
        run.snapshotPolicy(json,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))));
        return run;
    }
    @Test void unapprovedRunIsAuthorizedOnlyWhenSnapshotDisablesHumanApproval() throws Exception {
        assertTrue(run(false,true).isDeliveryAuthorized());
        assertFalse(run(true,true).isDeliveryAuthorized());
        assertTrue(run(false,true).allowsAutomaticMerge());
        assertFalse(run(false,false).allowsAutomaticMerge());
    }
    @Test void missingSnapshotStillRequiresApproval() {
        FeatureRun legacy=new FeatureRun("acme/app","issue-1","test","spec",5,"GENERIC",1);
        assertTrue(legacy.requiresHumanApproval());
        assertFalse(legacy.isDeliveryAuthorized());
        assertFalse(legacy.allowsAutomaticMerge());
    }
    @Test void authorizationDoesNotBypassVerificationState() throws Exception {
        assertThrows(IllegalStateException.class,()->run(false,true).completeDelivery());
    }
}

package io.github.surezzzzzz.sdk.iam.feign.client.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.iam.feign.client.model.IamPermissionManifestResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** IAM Feign Jakarta manifest wire 需与 SB2 形态同标保留 DATA 申报。 */
class IamPermissionManifestWireTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldMapDeclaredDataResourcesWithoutChangingPageAndApiFields() throws Exception {
        IamPermissionManifestResponse manifest = mapper.readValue(
                "{\"manifestVersion\":2,\"manifestDigest\":\"sha\",\"pagePermissions\":[\"app.page\"],"
                        + "\"apiPermissions\":[\"app.api\"],\"dataResources\":[{\"resource\":\"license-grant\","
                        + "\"actions\":[\"read\"],\"dimensions\":[\"grantId\",\"customerId\"]}]}",
                IamPermissionManifestResponse.class);

        assertEquals(Long.valueOf(2L), manifest.manifestVersion);
        assertEquals("sha", manifest.manifestDigest);
        assertEquals("license-grant", manifest.dataResources.get(0).resource);
        assertEquals("customerId", manifest.dataResources.get(0).dimensions.get(1));
    }

    @Test
    void missingDataDeclarationRemainsNullRatherThanPretendingToBeEmpty() throws Exception {
        IamPermissionManifestResponse manifest = mapper.readValue(
                "{\"manifestVersion\":1,\"manifestDigest\":\"sha\",\"pagePermissions\":[],\"apiPermissions\":[]}",
                IamPermissionManifestResponse.class);

        assertNull(manifest.dataResources);
    }
}

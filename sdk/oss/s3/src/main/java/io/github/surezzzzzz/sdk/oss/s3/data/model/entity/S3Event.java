package io.github.surezzzzzz.sdk.oss.s3.data.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/9/9 12:02
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class S3Event {
    @JsonProperty("Records")
    private List<Record> records;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Record {
        private String eventVersion;
        private String eventSource;
        private String eventTime;
        private String eventName;
        private UserIdentity userIdentity;
        private RequestParameters requestParameters;
        private ResponseElements responseElements;
        private S3 s3;

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UserIdentity {
            private String principalId;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RequestParameters {
            private String sourceIPAddress;

        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ResponseElements {
            private String xAmzRequestId;
            private String xAmzId2;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class S3 {
            private String s3SchemaVersion;
            private String configurationId;
            private Bucket bucket;
            private S3Object object;

            @Getter
            @Setter
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Bucket {
                private String arn;
                private String name;
                private OwnerIdentity ownerIdentity;

                @Getter
                @Setter
                @NoArgsConstructor
                @AllArgsConstructor
                public static class OwnerIdentity {
                    private String principalId;
                }
            }

            @Getter
            @Setter
            @NoArgsConstructor
            @AllArgsConstructor
            public static class S3Object {
                private String key;
                private long size;
                private String etag;
                private String versionId;
                private String sequencer;

            }

        }

    }

}

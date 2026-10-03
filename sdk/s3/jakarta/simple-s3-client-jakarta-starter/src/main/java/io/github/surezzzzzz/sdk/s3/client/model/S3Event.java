package io.github.surezzzzzz.sdk.s3.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * S3 Event Notification
 *
 * @author surezzzzzz
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3Event {

    @JsonProperty("Records")
    private List<Record> records;

    /**
     * S3 Event Record
     *
     * @author surezzzzzz
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Record {

        private String eventVersion;

        private String eventSource;

        private String eventTime;

        private String eventName;

        private UserIdentity userIdentity;

        private RequestParameters requestParameters;

        private ResponseElements responseElements;

        private S3 s3;

        /**
         * S3 Event Record User Identity
         *
         * @author surezzzzzz
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class UserIdentity {

            private String principalId;
        }

        /**
         * S3 Event Record Request Parameters
         *
         * @author surezzzzzz
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RequestParameters {

            private String sourceIPAddress;
        }

        /**
         * S3 Event Record Response Elements
         *
         * @author surezzzzzz
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ResponseElements {

            @JsonProperty("x-amz-request-id")
            private String xAmzRequestId;

            @JsonProperty("x-amz-id-2")
            private String xAmzId2;
        }

        /**
         * S3 Event Record S3
         *
         * @author surezzzzzz
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class S3 {

            private String s3SchemaVersion;

            private String configurationId;

            private Bucket bucket;

            private S3Object object;

            /**
             * S3 Event Record S3 Bucket
             *
             * @author surezzzzzz
             */
            @Getter
            @Setter
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Bucket {

                @JsonProperty("arn")
                private String arn;

                private String name;

                private OwnerIdentity ownerIdentity;

                /**
                 * S3 Event Record S3 Bucket Owner Identity
                 *
                 * @author surezzzzzz
                 */
                @Getter
                @Setter
                @NoArgsConstructor
                @AllArgsConstructor
                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class OwnerIdentity {

                    private String principalId;
                }
            }

            /**
             * S3 Event Record S3 Object
             *
             * @author surezzzzzz
             */
            @Getter
            @Setter
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class S3Object {

                private String key;

                private long size;

                @JsonProperty("eTag")
                private String eTag;

                private String versionId;

                private String sequencer;
            }
        }
    }
}

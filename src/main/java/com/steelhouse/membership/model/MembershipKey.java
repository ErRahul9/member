package com.steelhouse.membership.model;


import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import org.springframework.data.annotation.Id;

@DynamoDBTable(tableName = "memberships")
public class MembershipKey {

    @Id
    private String ip;
    private String segment;

    @DynamoDBHashKey(attributeName = "ip")
    public String getIp() {
        return this.ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @DynamoDBRangeKey(attributeName = "segment")
    public String getSegment() {
        return this.segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        com.steelhouse.membership.model.MembershipKey that = (com.steelhouse.membership.model.MembershipKey) o;
        return java.util.Objects.equals(ip, that.ip) &&
                java.util.Objects.equals(segment, that.segment);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(ip, segment);
    }
}

package com.steelhouse.membership.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import org.springframework.data.annotation.Id;


@DynamoDBTable(tableName = "memberships")
public class Membership {

    @Id
    private MembershipKey membershipKey;

    private String source;
    private Long epoch;
    private Integer aid;

    @DynamoDBHashKey(attributeName = "ip")
    public String getIp() {
        return membershipKey.getIp();
    }

    public void setIp(String ip) {
        if (membershipKey == null) {
            membershipKey = new MembershipKey();
        }
        this.membershipKey.setIp(ip);
    }

    @DynamoDBRangeKey(attributeName = "segment")
    public String getSegment() {
        return membershipKey.getSegment();
    }

    public void setSegment(String segment) {
        if (membershipKey == null) {
            membershipKey = new MembershipKey();
        }
        this.membershipKey.setSegment(segment);
    }

    @DynamoDBAttribute(attributeName = "source")
    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @DynamoDBAttribute(attributeName = "epoch")
    public Long getEpoch() {
        return epoch;
    }

    public void setEpoch(Long epoch) {
        this.epoch = epoch;
    }

    @DynamoDBAttribute(attributeName = "aid")
    public Integer getAid() {
        return aid;
    }

    public void setAid(Integer aid) {
        this.aid = aid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Membership that = (Membership) o;
        return java.util.Objects.equals(membershipKey, that.membershipKey) &&
                java.util.Objects.equals(source, that.source) &&
                java.util.Objects.equals(epoch, that.epoch) &&
                java.util.Objects.equals(aid, that.aid);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(membershipKey, source, epoch, aid);
    }
}


package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

import java.util.List;

/**
 * Client 列表查询条件（可选字段对象；clientIds 上限 100）
 *
 * @author surezzzzzz
 */
public class ListClientsQuery {

    /**
     * 按 Client 标识批量查询（上限 100）
     */
    private List<String> clientIds;

    /**
     * 按归属用户过滤
     */
    private String ownerUserId;

    /**
     * 按类型过滤（platform / user）
     */
    private String type;

    /**
     * 页码（默认 1）
     */
    private Integer page;

    /**
     * 每页数量（默认 20）
     */
    private Integer size;


    public List<String> getClientIds() {
        return clientIds;
    }

    public void setClientIds(List<String> clientIds) {
        this.clientIds = clientIds;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}

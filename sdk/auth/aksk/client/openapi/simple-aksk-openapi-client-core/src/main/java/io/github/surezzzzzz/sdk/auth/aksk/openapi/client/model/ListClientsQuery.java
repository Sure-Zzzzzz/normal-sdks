package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

/**
 * Client 分页列表查询条件（可选字段对象；按标识批量查询走 listClientsByClientIds）
 *
 * @author surezzzzzz
 */
public class ListClientsQuery {

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

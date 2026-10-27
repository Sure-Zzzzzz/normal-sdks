package io.github.surezzzzzz.sdk.ticket.server.service;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketGroupHasOpenTicketsException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketGroupNotFoundException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketTypeNotFoundException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketResourceConflictException;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketGroup;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketType;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketGroupRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketTypeRepository;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

/**
 * 类型与处理组管理（admin 面；停用不禁存量，在途单禁删组——DESIGN §6.1/§6.2）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
public class TypeGroupAdminService {

    private final TicketTypeRepository typeRepository;

    private final TicketGroupRepository groupRepository;

    private final TicketRepository ticketRepository;

    private final TicketTemplateValidator templateValidator;

    public TypeGroupAdminService(TicketTypeRepository typeRepository, TicketGroupRepository groupRepository,
                                 TicketRepository ticketRepository, TicketTemplateValidator templateValidator) {
        this.typeRepository = typeRepository;
        this.groupRepository = groupRepository;
        this.ticketRepository = ticketRepository;
        this.templateValidator = templateValidator;
    }

    /**
     * 全部类型。
     *
     * @return 类型列表
     */
    public List<TicketType> types() {
        return typeRepository.findAll();
    }

    /**
     * 新建/更新类型（模板变更不重校验存量单；code 唯一）。
     *
     * @param type 类型
     * @return 持久化后类型
     */
    public TicketType saveType(TicketType type) {
        templateValidator.validateTemplate(type.getFormTemplate());
        if (type.getId() == null) {
            try {
                typeRepository.insert(type);
            } catch (DuplicateKeyException e) {
                throw new TicketResourceConflictException();
            }
        } else {
            typeRepository.update(type);
        }
        return typeRepository.findByCode(type.getCode()).orElseThrow(TicketTypeNotFoundException::new);
    }

    /**
     * 全部处理组。
     *
     * @return 组列表
     */
    public List<TicketGroup> groups() {
        return groupRepository.findAll();
    }

    /**
     * 新建/更新处理组（组员增删即时生效；在途单禁删——只停用）。
     *
     * @param group 组
     * @return 持久化后组
     */
    public TicketGroup saveGroup(TicketGroup group) {
        if (group.getId() == null) {
            try {
                groupRepository.insert(group);
            } catch (DuplicateKeyException e) {
                throw new TicketResourceConflictException();
            }
        } else {
            groupRepository.update(group);
        }
        return groupRepository.findByCode(group.getCode()).orElseThrow(TicketGroupNotFoundException::new);
    }

    /**
     * 删除组（存在非终态工单时拒绝）。
     *
     * @param code 组编码
     */
    public void deleteGroup(String code) {
        TicketGroup group = groupRepository.findByCode(code).orElseThrow(TicketGroupNotFoundException::new);
        boolean hasOpen = !ticketRepository.findQueue(java.util.Collections.singletonList(code),
                java.util.Arrays.asList(io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus.SUBMITTED,
                        io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus.PROCESSING,
                        io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus.PENDING_INFO,
                        io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus.RESOLVED),
                0, 1).isEmpty();
        if (hasOpen) {
            throw new TicketGroupHasOpenTicketsException();
        }
        group.setEnabled(false);
        groupRepository.update(group);
    }
}

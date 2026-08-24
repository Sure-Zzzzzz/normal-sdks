(function () {
    'use strict';

    var shell = document.querySelector('.ops-shell');
    if (!shell) {
        return;
    }

    var apiBasePath = shell.getAttribute('data-api-base-path');
    var auditMaxRangeDays = Number(shell.getAttribute('data-audit-max-range-days'));
    var workspaceNames = ['elasticsearch', 'redis', 'kafka', 'mysql'];
    var sectionNames = ['overview', 'console', 'audit'];
    var activeWorkspace = 'elasticsearch';
    var activeSection = 'overview';
    var workspaceStates = {};
    var resultStates = {};
    var resultPageSize = 20;
    var auditTimeZone = 'beijing';
    var auditTimeZoneStorageKey = 'middleware-ops-audit-time-zone:v1:' + window.location.origin + ':' + apiBasePath;
    var auditTimeZoneSelect = document.getElementById('ops-audit-time-zone');
    var defaultConsolePanels = {
        elasticsearch: 'query',
        redis: 'key-discovery',
        kafka: 'topic-list',
        mysql: 'select'
    };

    workspaceNames.forEach(function (workspace) {
        workspaceStates[workspace] = {
            consolePanel: defaultConsolePanels[workspace],
            catalog: null,
            catalogInitialized: false,
            catalogLoading: false,
            catalogDetails: null,
            catalogDetailsLoading: false,
            selectedDatasourceKey: null,
            auditPage: 1,
            auditLoading: false,
            auditLoaded: false,
            auditRequested: false,
            auditPending: false,
            auditPendingRange: '',
            auditPendingFrom: null,
            auditPendingTo: null,
            auditRange: '',
            auditFrom: null,
            auditTo: null,
            auditHasMore: false,
            auditData: null,
            auditPendingSnapshot: null,
            auditSequence: 0,
            auditController: null,
            indexDatasourceKey: null,
            indexItems: [],
            indexTruncated: false,
            indexError: null,
            indexLoading: false,
            indexSequence: 0,
            indexController: null,
            fieldDatasourceKey: null,
            fieldIndex: null,
            fieldItems: [],
            fieldTruncated: false,
            fieldError: null,
            fieldLoading: false,
            fieldSequence: 0,
            fieldController: null,
            dslSuggestionItems: [],
            dslSuggestionIndex: -1,
            documentData: null,
            documentDatasourceKey: null,
            documentError: null,
            documentLoading: false,
            documentSequence: 0,
            documentController: null,
            mysqlTableDatasourceKey: null,
            mysqlTableItems: [],
            mysqlTableTruncated: false,
            mysqlTableError: null,
            mysqlTableLoading: false,
            mysqlTableSequence: 0,
            mysqlTableController: null,
            mysqlColumnDatasourceKey: null,
            mysqlColumnTable: null,
            mysqlColumnItems: [],
            mysqlSelectedColumnTable: null,
            mysqlSelectedColumnNames: [],
            mysqlColumnError: null,
            mysqlColumnLoading: false,
            mysqlColumnSequence: 0,
            mysqlColumnController: null,
            mysqlSqlSuggestionItems: [],
            mysqlSqlSuggestionIndex: -1,
            mysqlSqlSuggestionInputId: null
        };
    });

    function resetKafkaTopicConsoleState(message) {
        document.getElementById('kafka-topic-console-state').textContent = message;
    }

    var draftStorageVersion = 'v1';
    var draftStorageKey = 'middleware-ops-console-drafts:' + draftStorageVersion + ':' + window.location.origin + ':' + apiBasePath;
    var draftSaveTimer = null;

    function draftFields() {
        return Array.prototype.slice.call(document.querySelectorAll('.ops-workspace .ops-section[data-section-panel="console"] form input[name], .ops-workspace .ops-section[data-section-panel="console"] form textarea[name], .ops-workspace .ops-section[data-section-panel="console"] form select[name]'));
    }

    function setDraftState(message) {
        document.getElementById('ops-draft-state').textContent = message;
    }

    function consoleDrafts() {
        var drafts = {};
        draftFields().forEach(function (field) {
            if (field.type !== 'hidden' && field.type !== 'submit' && field.type !== 'button') {
                drafts[field.closest('.ops-workspace').getAttribute('data-workspace-panel') + ':' + field.form.id + ':' + field.name]
                    = field.value;
            }
        });
        workspaceNames.forEach(function (workspace) {
            drafts[workspace + ':datasourceKey'] = workspaceStates[workspace].selectedDatasourceKey || '';
        });
        drafts['mysql:selectedColumnTable'] = workspaceStates.mysql.mysqlSelectedColumnTable || '';
        drafts['mysql:selectedColumnNames'] = workspaceStates.mysql.mysqlSelectedColumnNames.join(',');
        return drafts;
    }

    function scheduleDraftSave() {
        if (draftSaveTimer) {
            window.clearTimeout(draftSaveTimer);
        }
        draftSaveTimer = window.setTimeout(function () {
            try {
                window.localStorage.setItem(draftStorageKey, JSON.stringify(consoleDrafts()));
                setDraftState('输入草稿仅保存在当前浏览器。');
            } catch (error) {
                setDraftState('当前浏览器无法保存输入草稿。');
            }
        }, 200);
    }

    function clearConsoleDrafts() {
        if (draftSaveTimer) {
            window.clearTimeout(draftSaveTimer);
            draftSaveTimer = null;
        }
        try {
            window.localStorage.removeItem(draftStorageKey);
            setDraftState('输入草稿已清除。');
        } catch (error) {
            setDraftState('当前浏览器无法清除输入草稿。');
        }
    }

    function restoreConsoleDrafts() {
        var drafts;
        try {
            drafts = JSON.parse(window.localStorage.getItem(draftStorageKey) || '{}');
        } catch (error) {
            setDraftState('已忽略无效的本地输入草稿。');
            return;
        }
        if (!drafts || typeof drafts !== 'object') {
            return;
        }
        draftFields().forEach(function (field) {
            var workspace = field.closest('.ops-workspace').getAttribute('data-workspace-panel');
            var key = workspace + ':' + field.form.id + ':' + field.name;
            var value = drafts[key];
            if (typeof value === 'string' && value.length <= 8192) {
                field.value = value;
            }
        });
        workspaceNames.forEach(function (workspace) {
            var datasourceKey = drafts[workspace + ':datasourceKey'];
            if (typeof datasourceKey === 'string' && datasourceKey.length <= 256) {
                workspaceStates[workspace].selectedDatasourceKey = datasourceKey || null;
            }
        });
        var selectedColumnTable = drafts['mysql:selectedColumnTable'];
        var selectedColumnNames = drafts['mysql:selectedColumnNames'];
        if (typeof selectedColumnTable === 'string' && isMysqlTableName(selectedColumnTable)
            && typeof selectedColumnNames === 'string' && selectedColumnNames.length <= 8192) {
            workspaceStates.mysql.mysqlSelectedColumnTable = selectedColumnTable;
            workspaceStates.mysql.mysqlSelectedColumnNames = selectedColumnNames.split(',').filter(stateColumnName);
        }
        if (Object.keys(drafts).length) {
            setDraftState('已恢复未执行的输入草稿。');
        }
    }

    function clearWorkspaceMemory() {
        clearConsoleDrafts();
        workspaceNames.forEach(function (workspace) {
            var state = workspaceStates[workspace];
            state.consolePanel = defaultConsolePanels[workspace];
            state.catalog = null;
            state.catalogInitialized = false;
            state.catalogLoading = false;
            state.catalogDetails = null;
            state.catalogDetailsLoading = false;
            state.selectedDatasourceKey = null;
            cancelAuditRequest(state);
            state.auditPage = 1;
            state.auditLoading = false;
            state.auditLoaded = false;
            state.auditRequested = false;
            state.auditPending = false;
            state.auditPendingRange = '';
            state.auditPendingFrom = null;
            state.auditPendingTo = null;
            state.auditRange = '';
            state.auditFrom = null;
            state.auditTo = null;
            state.auditHasMore = false;
            state.auditData = null;
            state.auditPendingSnapshot = null;
            cancelIndexRequest(state);
            cancelDocumentRequest(state);
            cancelElasticsearchFieldRequest(state);
            state.indexDatasourceKey = null;
            state.indexItems = [];
            state.indexTruncated = false;
            state.indexError = null;
            state.indexLoading = false;
            state.fieldDatasourceKey = null;
            state.fieldIndex = null;
            state.fieldItems = [];
            state.fieldTruncated = false;
            state.fieldError = null;
            state.fieldLoading = false;
            state.documentData = null;
            state.documentDatasourceKey = null;
            state.documentError = null;
            state.documentLoading = false;
            if (workspace === 'mysql') {
                clearMysqlSuggestions(state);
            }
            resetConsolePanel(workspace);
        });
        Array.prototype.forEach.call(document.querySelectorAll('.ops-workspace .ops-section[data-section-panel="console"] form'),
            function (form) {
                form.reset();
            });
        Array.prototype.forEach.call(document.querySelectorAll('.ops-result'), function (result) {
            setResult(result.id, '会话已失效，查询结果已清除。');
        });
        resetKafkaTopicConsoleState('会话已失效，查询状态已清除。');
    }

    function request(path, signal) {
        return fetch(apiBasePath + path, {
            credentials: 'same-origin',
            cache: 'no-store',
            headers: {'Accept': 'application/json'},
            signal: signal
        }).then(function (response) {
            return response.text().then(function (body) {
                var result = null;
                if (body) {
                    try {
                        result = JSON.parse(body);
                    } catch (error) {
                        result = null;
                    }
                }
                if (!response.ok) {
                    if (response.status === 401) {
                        clearWorkspaceMemory();
                        window.location.assign(window.location.pathname);
                    }
                    throw new Error(result && result.message ? result.message : '查询暂不可用');
                }
                return result;
            });
        });
    }

    function setResult(id, content) {
        delete resultStates[id];
        renderResult(id, content);
    }

    function resultValue(value) {
        return value === null || value === undefined || value === '' ? '-' : String(value);
    }

    function booleanValue(value, positive, negative) {
        return value === true ? positive : value === false ? negative : '-';
    }

    function clearResultNode(id) {
        var result = document.getElementById(id);
        result.textContent = '';
        return result;
    }

    function appendResultCell(row, value, className) {
        var cell = document.createElement('td');
        cell.textContent = resultValue(value);
        cell.title = resultValue(value);
        if (className) {
            cell.className = className;
        }
        row.appendChild(cell);
        return cell;
    }

    function appendResultAction(row, label, ariaLabel, click) {
        var cell = document.createElement('td');
        var button = document.createElement('button');
        button.className = 'ops-result-action';
        button.type = 'button';
        button.textContent = label;
        button.setAttribute('aria-label', ariaLabel);
        button.addEventListener('click', click);
        cell.appendChild(button);
        row.appendChild(cell);
    }

    function renderResultTable(id, caption, headers, items, appendRow) {
        var result = clearResultNode(id);
        var wrap = document.createElement('div');
        wrap.className = 'ops-result-table-wrap';
        var table = document.createElement('table');
        table.className = 'ops-result-table';
        var tableCaption = document.createElement('caption');
        tableCaption.textContent = caption;
        table.appendChild(tableCaption);
        var head = document.createElement('thead');
        var headRow = document.createElement('tr');
        headers.forEach(function (header) {
            var cell = document.createElement('th');
            cell.scope = 'col';
            cell.textContent = header;
            headRow.appendChild(cell);
        });
        head.appendChild(headRow);
        table.appendChild(head);
        var body = document.createElement('tbody');
        if (!items || !items.length) {
            var emptyRow = document.createElement('tr');
            var emptyCell = document.createElement('td');
            emptyCell.colSpan = headers.length;
            emptyCell.textContent = '当前查询没有可展示的数据。';
            emptyRow.appendChild(emptyCell);
            body.appendChild(emptyRow);
        } else {
            items.forEach(function (item) {
                var row = document.createElement('tr');
                appendRow(row, item);
                body.appendChild(row);
            });
        }
        table.appendChild(body);
        wrap.appendChild(table);
        result.appendChild(wrap);
    }

    function renderStatusCard(id, title, fields, note) {
        var result = clearResultNode(id);
        var card = document.createElement('section');
        card.className = 'ops-status-card';
        var heading = document.createElement('h2');
        heading.textContent = title;
        card.appendChild(heading);
        var list = document.createElement('dl');
        list.className = 'ops-status-card-grid';
        fields.forEach(function (field) {
            var term = document.createElement('dt');
            term.textContent = field.label;
            var description = document.createElement('dd');
            description.textContent = resultValue(field.value);
            description.title = resultValue(field.value);
            list.appendChild(term);
            list.appendChild(description);
        });
        card.appendChild(list);
        if (note) {
            var explanation = document.createElement('p');
            explanation.className = 'ops-status-card-note';
            explanation.textContent = note;
            card.appendChild(explanation);
        }
        result.appendChild(card);
    }

    function renderRedisKeyDiscovery(content) {
        renderResultTable('redis-key-discovery-console-result', 'Redis Key 发现结果', ['Key', '操作'], content.items,
            function (row, item) {
                appendResultCell(row, item, 'ops-result-long-value');
                appendResultAction(row, '填入精确 Key', '填入精确 Key：' + resultValue(item), function () {
                    fillRedisKey(item);
                });
            });
        var result = document.getElementById('redis-key-discovery-console-result');
        var summary = document.createElement('p');
        summary.className = 'ops-state';
        summary.textContent = '已返回 ' + resultValue(content.returned) + ' 项，结果上限 '
            + resultValue(content.limit) + '，遍历' + booleanValue(content.traversalComplete, '已完成', '未完成')
            + '，停止原因：' + resultValue(content.stopReason) + '。';
        result.appendChild(summary);
    }

    function renderRedisDatasourceList(content) {
        renderResultTable('redis-datasource-console-result', 'Redis 数据源摘要',
            ['数据源', '版本探测', 'Redis 版本', '部署模式'], content.items, function (row, item) {
                appendResultCell(row, item.datasourceKey, 'ops-result-long-value');
                appendResultCell(row, booleanValue(item.versionKnown, '已探测', '未探测'));
                appendResultCell(row, item.version);
                appendResultCell(row, item.deploymentMode);
            });
    }

    function renderKafkaDatasourceDiagnostics(content) {
        renderResultTable('kafka-datasource-console-result', 'Kafka 数据源诊断',
            ['数据源', '诊断状态', '原因', '集群 ID', 'Broker 节点数', 'Controller'], content.items, function (row, item) {
                appendResultCell(row, item.datasourceKey, 'ops-result-long-value');
                appendResultCell(row, item.diagnosticStatus);
                appendResultCell(row, item.diagnosticReason, 'ops-result-long-value');
                appendResultCell(row, item.clusterId, 'ops-result-long-value');
                appendResultCell(row, item.nodeCount);
                appendResultCell(row, booleanValue(item.controllerVisible, '可见', '不可见'));
            });
    }

    function renderKafkaTopicList(content) {
        renderResultTable('kafka-topic-list-console-result', 'Kafka Topic 清单', ['Topic', '操作'], content.items,
            function (row, item) {
                appendResultCell(row, item.name, 'ops-result-long-value');
                appendResultAction(row, '填入 Topic', '填入 Topic：' + resultValue(item.name), function () {
                    fillKafkaTopic(item.name);
                });
            });
    }

    function renderKafkaGroupList(content) {
        renderResultTable('kafka-group-list-console-result', 'Kafka 消费组清单', ['消费组', '协议类型', '操作'],
            content.items, function (row, item) {
                appendResultCell(row, item.groupId, 'ops-result-long-value');
                appendResultCell(row, item.protocolType);
                appendResultAction(row, '填入消费组', '填入消费组：' + resultValue(item.groupId), function () {
                    fillKafkaGroup(item.groupId);
                });
            });
    }

    function renderMysqlSelect(content) {
        renderResultTable('mysql-select-console-result', '受控 SELECT 结果', content.columns, content.rows,
            function (row, values) {
                content.columns.forEach(function (column, index) {
                    appendResultCell(row, values[index]);
                });
            });
    }

    function renderMysqlExplain(content) {
        renderResultTable('mysql-explain-console-result', '受控 Explain 结果',
            ['SELECT 类型', '表', '访问类型', '候选索引', '实际索引', '索引长度', '引用', '估算行数', '过滤率', 'Extra'],
            content.items, function (row, item) {
                appendResultCell(row, item.selectType);
                appendResultCell(row, item.table);
                appendResultCell(row, item.accessType);
                appendResultCell(row, item.possibleKeys);
                appendResultCell(row, item.key);
                appendResultCell(row, item.keyLength);
                appendResultCell(row, item.ref);
                appendResultCell(row, item.estimatedRows);
                appendResultCell(row, item.filteredPercent);
                appendResultCell(row, item.extra);
            });
    }

    function renderMysqlTableList(content) {
        renderResultTable('mysql-table-list-console-result', 'MySQL 表和视图目录',
            ['名称', '类型', '存储引擎', '估算行数', '操作'], content.items, function (row, item) {
                appendResultCell(row, item.name, 'ops-result-long-value');
                appendResultCell(row, item.kind);
                appendResultCell(row, item.engine);
                appendResultCell(row, item.estimatedRows);
                appendResultAction(row, '填入表名', '填入表名：' + resultValue(item.name), function () {
                    fillMysqlTable(item.name);
                });
            });
        var result = document.getElementById('mysql-table-list-console-result');
        var summary = document.createElement('p');
        summary.className = 'ops-state';
        summary.textContent = '已返回 ' + resultValue(content.returned) + ' 项，结果上限 ' + resultValue(content.limit)
            + '，遍历' + booleanValue(content.traversalComplete, '已完成', '未完成')
            + '，停止原因：' + resultValue(content.stopReason) + '。';
        result.appendChild(summary);
    }

    function renderMysqlTableColumns(content) {
        renderResultTable('mysql-table-columns-console-result', 'MySQL 列目录',
            ['名称', '顺序', '数据类型', '列定义', '允许空值', '存在默认值', '键角色', 'Extra'], content.items,
            function (row, item) {
                appendResultCell(row, item.name, 'ops-result-long-value');
                appendResultCell(row, item.position);
                appendResultCell(row, item.dataType);
                appendResultCell(row, item.columnType, 'ops-result-long-value');
                appendResultCell(row, booleanValue(item.nullable, '是', '否'));
                appendResultCell(row, booleanValue(item.defaultPresent, '是', '否'));
                appendResultCell(row, item.keyRole);
                appendResultCell(row, item.extra);
            });
    }

    function renderMysqlTableIndexes(content) {
        renderResultTable('mysql-table-indexes-console-result', 'MySQL 索引目录',
            ['名称', '唯一', '类型', '可见性', '列'], content.items, function (row, item) {
                appendResultCell(row, item.name, 'ops-result-long-value');
                appendResultCell(row, booleanValue(item.unique, '是', '否'));
                appendResultCell(row, item.type);
                appendResultCell(row, item.visible === null || item.visible === undefined ? '当前版本未提供' : booleanValue(item.visible, '是', '否'));
                appendResultCell(row, (item.columns || []).join(', '), 'ops-result-long-value');
            });
    }

    function renderKafkaTopicConfig(content) {
        renderResultTable('kafka-topic-config-console-result', 'Kafka Topic 固定配置',
            ['配置项', '值', '来源', '只读'], content.items, function (row, item) {
                appendResultCell(row, item.name, 'ops-result-long-value');
                appendResultCell(row, item.value, 'ops-result-long-value');
                appendResultCell(row, item.source);
                appendResultCell(row, booleanValue(item.readOnly, '是', '否'));
            });
    }

    function renderKafkaGroupDetail(content) {
        renderResultTable('kafka-group-detail-console-result', 'Kafka 消费组详情', ['字段', '值'], [
            {label: '消费组', value: content.groupId},
            {label: '状态', value: content.state},
            {label: '协议类型', value: content.protocolType},
            {label: '分配状态', value: content.assignmentStatus},
            {label: '成员数', value: content.memberCount},
            {label: '结果截断', value: booleanValue(content.truncated, '是', '否')}
        ], function (row, item) {
            appendResultCell(row, item.label);
            appendResultCell(row, item.value, 'ops-result-long-value');
        });
        var result = document.getElementById('kafka-group-detail-console-result');
        var assignments = document.createElement('p');
        assignments.className = 'ops-result-note';
        assignments.textContent = '分配摘要：' + (content.assignments || []).map(function (item) {
            return item.topic + ' [' + (item.partitions || []).join(', ') + ']';
        }).join('；');
        result.appendChild(assignments);
    }

    function renderKafkaTopicRuntime(content) {
        renderResultTable('kafka-topic-console-result', 'Kafka Topic 运行态',
            ['Topic', '分区', 'Leader', '副本', 'ISR', '最早 Offset', '最新 Offset'], content.partitions,
            function (row, item) {
                appendResultCell(row, content.topic, 'ops-result-long-value');
                appendResultCell(row, item.partition);
                appendResultCell(row, item.leader);
                appendResultCell(row, (item.replicas || []).join(', '));
                appendResultCell(row, (item.inSyncReplicas || []).join(', '));
                appendResultCell(row, item.earliestOffset);
                appendResultCell(row, item.latestOffset);
            });
    }

    function renderKafkaLag(content) {
        renderResultTable('kafka-lag-console-result', 'Kafka 消费组积压',
            ['Topic', '分区', '已提交 Offset', '末端 Offset', '积压', '操作'], content.items,
            function (row, item) {
                appendResultCell(row, item.topic, 'ops-result-long-value');
                appendResultCell(row, item.partition);
                appendResultCell(row, item.committedOffset);
                appendResultCell(row, item.endOffset);
                appendResultCell(row, item.lag);
                appendResultAction(row, '填入 Topic', '填入 Topic：' + resultValue(item.topic), function () {
                    fillKafkaTopic(item.topic);
                });
            });
    }

    function renderStructuredResult(id, content) {
        if (typeof content === 'string') {
            clearResultNode(id).textContent = content;
            return true;
        }
        if (!content || typeof content !== 'object') {
            return false;
        }
        if (id === 'redis-key-discovery-console-result' && Array.isArray(content.items)) {
            renderRedisKeyDiscovery(content);
            return true;
        }
        if (id === 'redis-datasource-console-result' && Array.isArray(content.items)) {
            renderRedisDatasourceList(content);
            return true;
        }
        if (id === 'redis-summary-console-result' && content.datasourceKey) {
            renderStatusCard(id, 'Redis 数据源摘要', [
                {label: '数据源', value: content.datasourceKey},
                {label: '版本探测', value: booleanValue(content.versionKnown, '已探测', '未探测')},
                {label: 'Redis 版本', value: content.version},
                {label: '部署模式', value: content.deploymentMode}
            ]);
            return true;
        }
        if (id === 'kafka-datasource-console-result' && Array.isArray(content.items)) {
            renderKafkaDatasourceDiagnostics(content);
            return true;
        }
        if (id === 'kafka-topic-list-console-result' && Array.isArray(content.items)) {
            renderKafkaTopicList(content);
            return true;
        }
        if (id === 'kafka-group-list-console-result' && Array.isArray(content.items)) {
            renderKafkaGroupList(content);
            return true;
        }
        if (id === 'kafka-topic-config-console-result' && Array.isArray(content.items)) {
            renderKafkaTopicConfig(content);
            return true;
        }
        if (id === 'kafka-group-detail-console-result' && content.groupId) {
            renderKafkaGroupDetail(content);
            return true;
        }
        if (id === 'kafka-topic-console-result' && Array.isArray(content.partitions)) {
            renderKafkaTopicRuntime(content);
            return true;
        }
        if (id === 'kafka-lag-console-result' && Array.isArray(content.items)) {
            renderKafkaLag(content);
            return true;
        }
        if (id === 'mysql-select-console-result' && Array.isArray(content.columns) && Array.isArray(content.rows)) {
            renderMysqlSelect(content);
            return true;
        }
        if (id === 'mysql-explain-console-result' && Array.isArray(content.items)) {
            renderMysqlExplain(content);
            return true;
        }
        if (id === 'mysql-table-list-console-result' && Array.isArray(content.items)) {
            renderMysqlTableList(content);
            return true;
        }
        if (id === 'mysql-table-columns-console-result' && Array.isArray(content.items)) {
            renderMysqlTableColumns(content);
            return true;
        }
        if (id === 'mysql-table-indexes-console-result' && Array.isArray(content.items)) {
            renderMysqlTableIndexes(content);
            return true;
        }
        if (id === 'mysql-status-console-result' && content.datasourceKey) {
            renderStatusCard(id, 'MySQL 探测状态', [
                {label: '数据源', value: content.datasourceKey},
                {label: '数据库', value: content.database},
                {label: '连接状态', value: booleanValue(content.connected, '已连接', '未连接')},
                {
                    label: '探测耗时', value: content.durationMillis === null || content.durationMillis === undefined
                        ? null : content.durationMillis + ' ms'
                },
                {label: '服务端版本', value: content.serverVersion},
                {label: '普通只读保护', value: booleanValue(content.readOnly, '已开启', '未开启')},
                {label: '强制只读保护', value: booleanValue(content.superReadOnly, '已开启', '未开启')}
            ], '普通只读保护限制普通账号写入；强制只读保护会同时限制具备更高权限的会话。两项均只反映 MySQL 服务端状态，不改变本控制台仅允许受控 SELECT 的边界。');
            return true;
        }
        return false;
    }

    function renderResultContent(id, content) {
        if (renderStructuredResult(id, content)) {
            return;
        }
        document.getElementById(id).textContent = typeof content === 'string' ? content : JSON.stringify(content, null, 2);
    }

    function renderResult(id, content) {
        renderResultContent(id, content);
        var pager = document.getElementById(id + '-pagination');
        if (pager) {
            pager.hidden = true;
        }
    }

    function resultCollection(data) {
        if (!data || typeof data !== 'object') {
            return null;
        }
        var keys = ['items', 'rows', 'entries', 'values', 'partitions'];
        for (var index = 0; index < keys.length; index++) {
            if (Array.isArray(data[keys[index]])) {
                return {path: [keys[index]], items: data[keys[index]]};
            }
        }
        if (data.value && typeof data.value === 'object') {
            for (var nestedIndex = 0; nestedIndex < keys.length; nestedIndex++) {
                if (Array.isArray(data.value[keys[nestedIndex]])) {
                    return {path: ['value', keys[nestedIndex]], items: data.value[keys[nestedIndex]]};
                }
            }
        }
        return null;
    }

    function resultPageData(state) {
        var copied = JSON.parse(JSON.stringify(state.data));
        var start = (state.page - 1) * resultPageSize;
        if (state.collection.path.length === 1) {
            copied[state.collection.path[0]] = state.collection.items.slice(start, start + resultPageSize);
        } else {
            copied[state.collection.path[0]][state.collection.path[1]] = state.collection.items.slice(start,
                start + resultPageSize);
        }
        return copied;
    }

    function renderResultPageContent(id, content) {
        renderResultContent(id, content);
    }

    function clearWorkspaceResults(workspace) {
        var state = workspaceStates[workspace];
        Array.prototype.forEach.call(workspacePanel(workspace).querySelectorAll('.ops-result'), function (result) {
            setResult(result.id, '切换数据源后已清除查询结果。');
        });
        if (workspace === 'kafka') {
            resetKafkaTopicConsoleState('切换数据源后已清除查询状态。');
        }
        if (workspace === 'mysql') {
            clearMysqlSuggestions(state);
            renderMysqlSuggestions(state);
        }
        if (workspace === 'elasticsearch') {
            cancelIndexRequest(state);
            cancelDocumentRequest(state);
            cancelElasticsearchFieldRequest(state);
            state.indexDatasourceKey = null;
            state.indexItems = [];
            state.indexTruncated = false;
            state.indexError = null;
            state.indexLoading = false;
            state.fieldDatasourceKey = null;
            state.fieldIndex = null;
            state.fieldItems = [];
            state.fieldTruncated = false;
            state.fieldError = null;
            state.fieldLoading = false;
            state.documentData = null;
            state.documentDatasourceKey = null;
            state.documentError = null;
            state.documentLoading = false;
            renderElasticsearchIndices(state);
            renderElasticsearchDocuments(state, '切换数据源后已清除查询结果。');
        }
    }

    function renderResultPage(id) {
        var state = resultStates[id];
        var pager = document.getElementById(id + '-pagination');
        if (!pager) {
            renderResultContent(id, state.data);
            return;
        }
        renderResultPageContent(id, resultPageData(state));
        var totalPages = Math.ceil(state.collection.items.length / resultPageSize);
        pager.hidden = false;
        document.getElementById(id + '-page').textContent = '第 ' + state.page + ' / ' + totalPages + ' 页，共 '
            + state.collection.items.length + ' 条。';
        document.getElementById(id + '-previous').disabled = state.page <= 1;
        document.getElementById(id + '-next').disabled = state.page >= totalPages;
    }

    function setPagedResult(id, data) {
        var collection = resultCollection(data);
        if (!collection || collection.items.length <= resultPageSize) {
            setResult(id, data);
            return;
        }
        resultStates[id] = {data: data, collection: collection, page: 1};
        renderResultPage(id);
    }

    function initializeResultPagination(id) {
        document.getElementById(id + '-previous').addEventListener('click', function () {
            var state = resultStates[id];
            if (state && state.page > 1) {
                state.page--;
                renderResultPage(id);
            }
        });
        document.getElementById(id + '-next').addEventListener('click', function () {
            var state = resultStates[id];
            if (state && state.page * resultPageSize < state.collection.items.length) {
                state.page++;
                renderResultPage(id);
            }
        });
    }

    function encodeBase64Url(value) {
        var bytes = new TextEncoder().encode(value);
        var binary = '';
        for (var i = 0; i < bytes.length; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    }

    function queryString(values) {
        var params = new URLSearchParams();
        Object.keys(values).forEach(function (key) {
            if (values[key] !== undefined && values[key] !== null && values[key] !== '') {
                params.append(key, values[key]);
            }
        });
        return params.toString();
    }

    function tagText(value) {
        return value === null || value === undefined || value === '' ? '未配置' : value;
    }

    function datasourceLabel(item) {
        return item.datasourceKey + ' · ' + tagText(item.clusterTag);
    }

    function workspacePanel(workspace) {
        return document.querySelector('[data-workspace-panel="' + workspace + '"]');
    }

    function workspaceSelects(workspace) {
        return workspacePanel(workspace).querySelectorAll('.ops-datasource-select');
    }

    function setConsolePanelExpanded(workspace, panelName) {
        var state = workspaceStates[workspace];
        var panel = workspacePanel(workspace).querySelector('[data-console-panel="' + panelName + '"]');
        if (!panel) {
            return;
        }
        state.consolePanel = state.consolePanel === panelName ? null : panelName;
        Array.prototype.forEach.call(workspacePanel(workspace).querySelectorAll('[data-console-panel]'), function (item) {
            var expanded = item.getAttribute('data-console-panel') === state.consolePanel;
            var toggle = item.querySelector('[data-console-panel-toggle]');
            var body = document.getElementById(toggle.getAttribute('aria-controls'));
            toggle.setAttribute('aria-expanded', String(expanded));
            body.hidden = !expanded;
        });
    }

    function resetConsolePanel(workspace) {
        workspaceStates[workspace].consolePanel = null;
        setConsolePanelExpanded(workspace, defaultConsolePanels[workspace]);
    }

    function fillRedisKey(key) {
        document.querySelector('#redis-console-form input[name="key"]').value = key;
        setConsolePanelExpanded('redis', 'key-read');
        scheduleDraftSave();
    }

    function fillKafkaTopic(topic) {
        document.querySelector('#kafka-topic-console-form input[name="topic"]').value = topic;
        setConsolePanelExpanded('kafka', 'topic-runtime');
        scheduleDraftSave();
    }

    function fillKafkaGroup(groupId) {
        document.querySelector('#kafka-lag-console-form input[name="groupId"]').value = groupId;
        setConsolePanelExpanded('kafka', 'group-lag');
        scheduleDraftSave();
    }

    function selectedDatasource(workspace) {
        var state = workspaceStates[workspace];
        var items = state.catalog && state.catalog.items ? state.catalog.items : [];
        return items.filter(function (item) {
            return item.datasourceKey === state.selectedDatasourceKey;
        })[0] || null;
    }

    function setSelectedDatasource(workspace, datasourceKey) {
        var state = workspaceStates[workspace];
        var item = (state.catalog && state.catalog.items || []).filter(function (entry) {
            return entry.datasourceKey === datasourceKey;
        })[0];
        if (item && state.selectedDatasourceKey === item.datasourceKey) {
            return;
        }
        state.selectedDatasourceKey = item ? item.datasourceKey : null;
        clearWorkspaceResults(workspace);
        scheduleDraftSave();
        if (workspace === 'elasticsearch') {
            if (item) {
                loadElasticsearchIndices(item.datasourceKey);
            }
            updateElasticsearchFieldCapabilities();
        }
        Array.prototype.forEach.call(workspaceSelects(workspace), function (select) {
            select.value = state.selectedDatasourceKey || '';
        });
        renderCatalog(workspace, state.catalog);
    }

    function renderDatasourceSelectors(workspace, items) {
        var state = workspaceStates[workspace];
        var selectedKey = state.selectedDatasourceKey;
        var selected = selectedDatasource(workspace);
        if (!selected) {
            state.selectedDatasourceKey = !state.catalogInitialized && items.length ? items[0].datasourceKey : null;
            if (state.catalogInitialized && selectedKey) {
                clearWorkspaceResults(workspace);
            }
        }
        state.catalogInitialized = true;
        selected = selectedDatasource(workspace);
        if (workspace === 'elasticsearch' && selected && state.indexDatasourceKey !== selected.datasourceKey) {
            loadElasticsearchIndices(selected.datasourceKey);
        }
        if (workspace === 'elasticsearch') {
            updateElasticsearchFieldCapabilities();
        }
        Array.prototype.forEach.call(workspaceSelects(workspace), function (select) {
            select.textContent = '';
            var placeholder = document.createElement('option');
            placeholder.value = '';
            placeholder.textContent = items.length ? '请选择数据源' : '没有可查询数据源';
            select.appendChild(placeholder);
            items.forEach(function (item) {
                var option = document.createElement('option');
                option.value = item.datasourceKey;
                option.textContent = datasourceLabel(item);
                option.title = option.textContent;
                option.selected = selected && item.datasourceKey === selected.datasourceKey;
                select.appendChild(option);
            });
            select.disabled = !items.length;
        });
        updateDatasourceContext(workspace);
    }

    function requireDatasource(workspace) {
        var item = selectedDatasource(workspace);
        if (!item) {
            throw new Error('请先选择数据源。');
        }
        return item.datasourceKey;
    }

    function updateDatasourceContext(workspace) {
        var panel = workspacePanel(workspace);
        var item = selectedDatasource(workspace);
        Array.prototype.forEach.call(panel.querySelectorAll('[data-datasource-context]'), function (node) {
            node.textContent = item ? '当前数据源 · ' + datasourceLabel(item) : '请选择数据源';
        });
        Array.prototype.forEach.call(panel.querySelectorAll('.ops-datasource-card'), function (card) {
            var selected = item && card.getAttribute('data-datasource-key') === item.datasourceKey;
            card.classList.toggle('is-selected', selected);
            card.setAttribute('aria-pressed', selected ? 'true' : 'false');
        });
    }

    function elasticsearchVersionStatus(detail) {
        if (!detail.detected) {
            return '版本状态：尚未探测';
        }
        if (!detail.configuredVersion) {
            return '版本状态：已探测，未配置期望版本';
        }
        return detail.versionMismatch ? '版本状态：已验证不一致' : '版本状态：已验证一致';
    }

    function overviewLines(workspace, item, details) {
        var detail = details && details[item.datasourceKey];
        if (!detail) {
            return details ? ['安全摘要暂不可用。'] : ['安全摘要正在加载。'];
        }
        if (detail.error) {
            return ['安全摘要暂不可用。'];
        }
        if (workspace === 'elasticsearch') {
            return [
                '配置版本：' + tagText(detail.configuredVersion),
                '探测版本：' + tagText(detail.detectedVersion),
                '有效版本：' + tagText(detail.effectiveVersion),
                elasticsearchVersionStatus(detail)
            ];
        }
        if (workspace === 'redis') {
            return [
                '版本状态：' + (detail.versionKnown ? '已探测' : '未探测'),
                'Redis 版本：' + tagText(detail.version),
                '部署模式：' + tagText(detail.deploymentMode)
            ];
        }
        if (workspace === 'kafka') {
            return [
                '诊断状态：' + tagText(detail.diagnosticStatus),
                '诊断原因：' + tagText(detail.diagnosticReason),
                '集群标识：' + tagText(detail.clusterId),
                'Broker 节点：' + tagText(detail.nodeCount),
                'Controller：' + booleanValue(detail.controllerVisible, '可见', '不可见')
            ];
        }
        if (workspace === 'mysql') {
            return [
                '连接状态：' + booleanValue(detail.connected, '已连接', '未连接'),
                '逻辑数据库：' + tagText(detail.database),
                '服务端版本：' + tagText(detail.serverVersion),
                '只读保护：' + booleanValue(detail.readOnly, '已开启', '未开启'),
                '强制只读保护：' + booleanValue(detail.superReadOnly, '已开启', '未开启'),
                '探测耗时：' + tagText(detail.durationMillis) + ' ms'
            ];
        }
        return ['安全摘要暂不可用。'];
    }

    function renderCatalog(workspace, data) {
        var stateNode = document.getElementById(workspace + '-overview-state');
        var grid = document.getElementById(workspace + '-overview-grid');
        var items = data && data.items ? data.items : [];
        var details = workspaceStates[workspace].catalogDetails;
        grid.textContent = '';
        renderDatasourceSelectors(workspace, items);
        if (!items.length) {
            stateNode.textContent = '暂无可查询数据源。';
            return;
        }
        stateNode.textContent = '已加载 ' + items.length + ' 个数据源。';
        items.forEach(function (item) {
            var card = document.createElement('article');
            var title = document.createElement('h2');
            var tag = document.createElement('p');
            title.textContent = item.datasourceKey;
            title.title = item.datasourceKey;
            tag.className = 'ops-datasource-tag';
            tag.textContent = tagText(item.clusterTag);
            tag.title = tag.textContent;
            card.className = 'ops-datasource-card';
            card.classList.toggle('is-selected', item.datasourceKey === workspaceStates[workspace].selectedDatasourceKey);
            card.setAttribute('data-datasource-key', item.datasourceKey);
            card.setAttribute('role', 'button');
            card.setAttribute('tabindex', '0');
            card.setAttribute('aria-pressed', item.datasourceKey === workspaceStates[workspace].selectedDatasourceKey ? 'true' : 'false');
            card.addEventListener('click', function () {
                setSelectedDatasource(workspace, item.datasourceKey);
                window.location.hash = workspace + '/console';
            });
            card.addEventListener('keydown', function (event) {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    setSelectedDatasource(workspace, item.datasourceKey);
                    window.location.hash = workspace + '/console';
                }
            });
            card.appendChild(title);
            card.appendChild(tag);
            overviewLines(workspace, item, details).forEach(function (line) {
                var detail = document.createElement('p');
                detail.className = 'ops-datasource-detail';
                detail.textContent = line;
                detail.title = line;
                card.appendChild(detail);
            });
            grid.appendChild(card);
        });
    }

    function detailMap(items) {
        var details = {};
        items.forEach(function (item) {
            details[item.datasourceKey] = item;
        });
        return details;
    }

    function loadCatalogDetails(workspace, catalog) {
        var state = workspaceStates[workspace];
        var items = catalog.items || [];
        if (!items.length || state.catalogDetailsLoading || state.catalogDetails) {
            return;
        }
        state.catalogDetailsLoading = true;
        if (workspace === 'elasticsearch') {
            return Promise.all(items.map(function (item) {
                return request('/elasticsearch/datasources/' + encodeURIComponent(item.datasourceKey) + '/summary')
                    .then(function (detail) {
                        return {datasourceKey: item.datasourceKey, detail: detail};
                    })
                    .catch(function () {
                        return {datasourceKey: item.datasourceKey, detail: {error: true}};
                    });
            })).then(function (details) {
                var mapped = {};
                details.forEach(function (entry) {
                    mapped[entry.datasourceKey] = entry.detail;
                });
                return mapped;
            }).then(applyCatalogDetails);
        } else if (workspace === 'redis') {
            return request('/redis/datasources/overview').then(function (response) {
                return detailMap(response.items || []);
            }).catch(function () {
                var fallback = {};
                items.forEach(function (item) {
                    fallback[item.datasourceKey] = {error: true};
                });
                return fallback;
            }).then(applyCatalogDetails);
        } else if (workspace === 'kafka') {
            return request('/kafka/datasources/overview').then(function (response) {
                return detailMap(response.items || []);
            }).catch(function () {
                var fallback = {};
                items.forEach(function (item) {
                    fallback[item.datasourceKey] = {error: true};
                });
                return fallback;
            }).then(applyCatalogDetails);
        } else if (workspace === 'mysql') {
            return Promise.all(items.map(function (item) {
                return request('/mysql/datasources/' + encodeURIComponent(item.datasourceKey) + '/overview-status')
                    .then(function (detail) {
                        return {datasourceKey: item.datasourceKey, detail: detail};
                    })
                    .catch(function () {
                        return {datasourceKey: item.datasourceKey, detail: {error: true}};
                    });
            })).then(function (details) {
                var mapped = {};
                details.forEach(function (entry) {
                    mapped[entry.datasourceKey] = entry.detail;
                });
                return mapped;
            }).then(applyCatalogDetails);
        }
        return applyCatalogDetails({});

        function applyCatalogDetails(details) {
            if (state.catalog === catalog) {
                state.catalogDetails = details;
                renderCatalog(workspace, catalog);
            }
            return details;
        }
    }

    function loadCatalog(workspace) {
        var state = workspaceStates[workspace];
        if (state.catalog || state.catalogLoading) {
            return;
        }
        state.catalogLoading = true;
        document.getElementById(workspace + '-overview-state').textContent = '正在加载数据源。';
        request('/' + workspace + '/catalog').then(function (data) {
            state.catalog = data;
            renderCatalog(workspace, data);
            return loadCatalogDetails(workspace, data);
        }).catch(function (error) {
            document.getElementById(workspace + '-overview-state').textContent = error.message;
            Array.prototype.forEach.call(workspaceSelects(workspace), function (select) {
                select.disabled = true;
                select.options[0].textContent = '数据源目录加载失败';
            });
        }).then(function () {
            state.catalogLoading = false;
            state.catalogDetailsLoading = false;
        });
    }

    function cancelIndexRequest(state) {
        state.indexSequence++;
        if (state.indexController) {
            state.indexController.abort();
            state.indexController = null;
        }
    }

    function cancelDocumentRequest(state) {
        state.documentSequence++;
        if (state.documentController) {
            state.documentController.abort();
            state.documentController = null;
        }
    }

    function cancelElasticsearchFieldRequest(state) {
        state.fieldSequence++;
        if (state.fieldController) {
            state.fieldController.abort();
            state.fieldController = null;
        }
    }

    function isExactElasticsearchIndex(value) {
        return /^[a-z0-9][a-z0-9._+\-]*$/.test(value) && value.length <= 256;
    }

    function renderElasticsearchFieldState(state) {
        var node = document.getElementById('elasticsearch-field-state');
        if (!state.selectedDatasourceKey || !isExactElasticsearchIndex(document.getElementById('elasticsearch-index-input').value.trim())) {
            node.textContent = '字段补全仅支持精确索引；通配模式可直接执行查询。';
        } else if (state.fieldLoading) {
            node.textContent = '正在加载当前索引的字段能力。';
        } else if (state.fieldError) {
            node.textContent = '字段能力加载失败，仍可输入受限 JSON DSL。';
        } else if (state.fieldTruncated) {
            node.textContent = '已加载前 ' + state.fieldItems.length + ' 个安全字段（含 '
                + aggregatableElasticsearchFieldCount(state.fieldItems) + ' 个可聚合字段），候选已截断。';
        } else {
            node.textContent = '已加载 ' + state.fieldItems.length + ' 个安全字段（含 '
                + aggregatableElasticsearchFieldCount(state.fieldItems) + ' 个可聚合字段），用于当前 DSL 补全。';
        }
    }

    function aggregatableElasticsearchFieldCount(items) {
        return items.filter(function (item) {
            return item.aggregatable;
        }).length;
    }

    function loadElasticsearchFieldCapabilities(datasourceKey, index) {
        var state = workspaceStates.elasticsearch;
        cancelElasticsearchFieldRequest(state);
        state.fieldDatasourceKey = datasourceKey;
        state.fieldIndex = index;
        state.fieldItems = [];
        state.fieldTruncated = false;
        state.fieldError = null;
        state.fieldLoading = true;
        renderElasticsearchFieldState(state);
        var sequence = state.fieldSequence;
        var controller = new AbortController();
        state.fieldController = controller;
        request('/elasticsearch/datasources/' + encodeURIComponent(datasourceKey) + '/fields?'
            + queryString({index: index}), controller.signal).then(function (data) {
            if (state.fieldSequence !== sequence || state.selectedDatasourceKey !== datasourceKey
                || document.getElementById('elasticsearch-index-input').value.trim() !== index) {
                return;
            }
            state.fieldItems = Array.isArray(data.items) ? data.items : [];
            state.fieldTruncated = !!data.truncated;
        }).catch(function (error) {
            if (state.fieldSequence === sequence && error.name !== 'AbortError') {
                state.fieldError = true;
            }
        }).then(function () {
            if (state.fieldSequence === sequence) {
                state.fieldLoading = false;
                state.fieldController = null;
                renderElasticsearchFieldState(state);
            }
        });
    }

    function updateElasticsearchFieldCapabilities() {
        var state = workspaceStates.elasticsearch;
        var datasource = selectedDatasource('elasticsearch');
        var index = document.getElementById('elasticsearch-index-input').value.trim();
        if (!datasource || !isExactElasticsearchIndex(index)) {
            cancelElasticsearchFieldRequest(state);
            state.fieldDatasourceKey = null;
            state.fieldIndex = null;
            state.fieldItems = [];
            state.fieldTruncated = false;
            state.fieldError = null;
            state.fieldLoading = false;
            renderElasticsearchFieldState(state);
            return;
        }
        if (state.fieldDatasourceKey !== datasource.datasourceKey || state.fieldIndex !== index) {
            loadElasticsearchFieldCapabilities(datasource.datasourceKey, index);
        }
    }

    function cancelMysqlTableRequest(state) {
        state.mysqlTableSequence++;
        if (state.mysqlTableController) {
            state.mysqlTableController.abort();
            state.mysqlTableController = null;
        }
    }

    function cancelMysqlColumnRequest(state) {
        state.mysqlColumnSequence++;
        if (state.mysqlColumnController) {
            state.mysqlColumnController.abort();
            state.mysqlColumnController = null;
        }
    }

    function clearMysqlSuggestions(state) {
        cancelMysqlTableRequest(state);
        cancelMysqlColumnRequest(state);
        state.mysqlTableDatasourceKey = null;
        state.mysqlTableItems = [];
        state.mysqlTableTruncated = false;
        state.mysqlTableError = null;
        state.mysqlTableLoading = false;
        state.mysqlColumnDatasourceKey = null;
        state.mysqlColumnTable = null;
        state.mysqlColumnItems = [];
        state.mysqlSelectedColumnTable = null;
        state.mysqlSelectedColumnNames = [];
        state.mysqlColumnError = null;
        state.mysqlColumnLoading = false;
        closeMysqlSqlSuggestions();
        document.getElementById('mysql-query-table-options').textContent = '';
        document.getElementById('mysql-query-column-options').textContent = '';
        document.getElementById('mysql-query-helper-state').textContent = '选择数据源后可加载有界表候选；选定精确表后才可加载当前表的字段候选。';
    }

    function renderMysqlSuggestions(state) {
        var tableOptions = document.getElementById('mysql-query-table-options');
        var columnOptions = document.getElementById('mysql-query-column-options');
        var selectAllColumns = document.getElementById('mysql-select-all-columns');
        var stateNode = document.getElementById('mysql-query-helper-state');
        tableOptions.textContent = '';
        selectAllColumns.disabled = !state.mysqlColumnItems.length || !state.mysqlColumnTable;
        selectAllColumns.textContent = state.mysqlColumnItems.length
        && state.mysqlSelectedColumnNames.length === state.mysqlColumnItems.length ? '取消全选' : '全选字段';
        selectAllColumns.setAttribute('aria-pressed', String(!selectAllColumns.disabled
            && state.mysqlSelectedColumnNames.length === state.mysqlColumnItems.length));
        columnOptions.textContent = '';
        state.mysqlTableItems.forEach(function (item) {
            var option = document.createElement('option');
            option.value = item.name;
            tableOptions.appendChild(option);
        });
        if (state.mysqlColumnItems.length) {
            state.mysqlColumnItems.forEach(function (item) {
                var label = document.createElement('label');
                var input = document.createElement('input');
                input.type = 'checkbox';
                input.value = item.name;
                input.checked = state.mysqlSelectedColumnNames.indexOf(item.name) >= 0;
                input.addEventListener('change', function () {
                    var selected = state.mysqlSelectedColumnNames;
                    if (input.checked && selected.indexOf(item.name) < 0) {
                        selected.push(item.name);
                    } else if (!input.checked) {
                        state.mysqlSelectedColumnNames = selected.filter(function (name) {
                            return name !== item.name;
                        });
                    }
                    renderMysqlSuggestions(state);
                    scheduleDraftSave();
                });
                label.className = 'mysql-column-option';
                label.appendChild(input);
                label.appendChild(document.createTextNode(item.name));
                columnOptions.appendChild(label);
            });
        } else if (state.mysqlColumnLoading) {
            columnOptions.textContent = '正在加载字段候选。';
        } else if (state.mysqlColumnError) {
            columnOptions.textContent = '字段候选暂不可用。';
        } else {
            columnOptions.textContent = '加载字段候选后可多选。';
        }
        if (!state.selectedDatasourceKey) {
            stateNode.textContent = '请选择数据源后加载有界表候选。';
        } else if (state.mysqlTableLoading) {
            stateNode.textContent = '正在加载当前数据源的表候选。';
        } else if (state.mysqlColumnLoading) {
            stateNode.textContent = '正在加载当前表的字段候选。';
        } else if (state.mysqlTableError) {
            stateNode.textContent = state.mysqlTableError;
        } else if (state.mysqlColumnError) {
            stateNode.textContent = state.mysqlColumnError;
        } else if (state.mysqlColumnTable) {
            stateNode.textContent = '已加载表 ' + state.mysqlColumnTable + ' 的 ' + state.mysqlColumnItems.length + ' 个字段候选。';
        } else if (state.mysqlTableItems.length) {
            stateNode.textContent = '已加载 ' + state.mysqlTableItems.length + ' 个表候选'
                + (state.mysqlTableTruncated ? '，结果已截断；仍可手动输入精确表名。' : '。');
        } else {
            stateNode.textContent = '当前数据源暂无表候选，仍可手动输入精确表名。';
        }
    }

    function loadMysqlTableSuggestions(datasourceKey) {
        var state = workspaceStates.mysql;
        closeMysqlSqlSuggestions();
        cancelMysqlTableRequest(state);
        cancelMysqlColumnRequest(state);
        state.mysqlTableDatasourceKey = datasourceKey;
        state.mysqlTableItems = [];
        state.mysqlTableTruncated = false;
        state.mysqlTableError = null;
        state.mysqlTableLoading = true;
        state.mysqlColumnDatasourceKey = null;
        state.mysqlColumnTable = null;
        state.mysqlColumnItems = [];
        state.mysqlSelectedColumnTable = null;
        state.mysqlSelectedColumnNames = [];
        state.mysqlColumnError = null;
        state.mysqlColumnLoading = false;
        renderMysqlSuggestions(state);
        var sequence = state.mysqlTableSequence;
        var controller = new AbortController();
        state.mysqlTableController = controller;
        request('/mysql/datasources/' + encodeURIComponent(datasourceKey) + '/tables', controller.signal).then(function (data) {
            if (state.mysqlTableSequence !== sequence || state.selectedDatasourceKey !== datasourceKey) {
                return;
            }
            state.mysqlTableItems = Array.isArray(data.items) ? data.items : [];
            state.mysqlTableTruncated = !!data.truncated;
        }).catch(function (error) {
            if (state.mysqlTableSequence === sequence && error.name !== 'AbortError') {
                state.mysqlTableError = error.message + '，仍可手动输入精确表名。';
            }
        }).then(function () {
            if (state.mysqlTableSequence === sequence) {
                state.mysqlTableLoading = false;
                state.mysqlTableController = null;
                renderMysqlSuggestions(state);
            }
        });
    }

    function isMysqlTableName(value) {
        return /^[A-Za-z0-9_$]+$/.test(value);
    }

    function clearMysqlColumnSuggestions(state) {
        closeMysqlSqlSuggestions();
        cancelMysqlColumnRequest(state);
        state.mysqlColumnDatasourceKey = null;
        state.mysqlColumnTable = null;
        state.mysqlColumnItems = [];
        state.mysqlSelectedColumnTable = null;
        state.mysqlSelectedColumnNames = [];
        state.mysqlColumnError = null;
        state.mysqlColumnLoading = false;
    }

    function loadMysqlColumnSuggestions(datasourceKey, table) {
        var state = workspaceStates.mysql;
        closeMysqlSqlSuggestions();
        cancelMysqlColumnRequest(state);
        state.mysqlColumnDatasourceKey = datasourceKey;
        state.mysqlColumnTable = null;
        state.mysqlColumnItems = [];
        if (state.mysqlSelectedColumnTable !== table) {
            state.mysqlSelectedColumnTable = null;
            state.mysqlSelectedColumnNames = [];
        }
        state.mysqlColumnError = null;
        state.mysqlColumnLoading = true;
        renderMysqlSuggestions(state);
        var sequence = state.mysqlColumnSequence;
        var controller = new AbortController();
        state.mysqlColumnController = controller;
        request('/mysql/datasources/' + encodeURIComponent(datasourceKey) + '/tables/'
            + encodeURIComponent(table) + '/columns', controller.signal).then(function (data) {
            if (state.mysqlColumnSequence !== sequence || state.selectedDatasourceKey !== datasourceKey) {
                return;
            }
            state.mysqlColumnTable = table;
            state.mysqlColumnItems = Array.isArray(data.items) ? data.items : [];
            if (state.mysqlSelectedColumnTable === table) {
                state.mysqlSelectedColumnNames = state.mysqlSelectedColumnNames.filter(function (name) {
                    return state.mysqlColumnItems.some(function (item) {
                        return item.name === name;
                    });
                });
            } else {
                state.mysqlSelectedColumnTable = table;
            }
        }).catch(function (error) {
            if (state.mysqlColumnSequence === sequence && error.name !== 'AbortError') {
                state.mysqlColumnError = error.message + '，仍可手动编辑受控 SELECT。';
            }
        }).then(function () {
            if (state.mysqlColumnSequence === sequence) {
                state.mysqlColumnLoading = false;
                state.mysqlColumnController = null;
                renderMysqlSuggestions(state);
            }
        });
    }

    function fillMysqlTable(table) {
        document.getElementById('mysql-query-table-input').value = table;
        document.querySelector('#mysql-table-columns-console-form input[name="table"]').value = table;
        document.querySelector('#mysql-table-indexes-console-form input[name="table"]').value = table;
        if (workspaceStates.mysql.consolePanel !== 'table-columns') {
            setConsolePanelExpanded('mysql', 'table-columns');
        }
        scheduleDraftSave();
    }

    function insertMysqlDraft(formId) {
        var state = workspaceStates.mysql;
        var table = document.getElementById('mysql-query-table-input').value.trim();
        if (!isMysqlTableName(table)) {
            document.getElementById('mysql-query-helper-state').textContent = '请先选择或输入仅含字母、数字、下划线或 $ 的精确表名。';
            return;
        }
        if (state.mysqlColumnTable !== table) {
            document.getElementById('mysql-query-helper-state').textContent = '请先加载当前表的字段候选并勾选字段。';
            return;
        }
        var columns = state.mysqlColumnItems.map(function (item) {
            return item.name;
        }).filter(function (name) {
            return state.mysqlSelectedColumnNames.indexOf(name) >= 0 && stateColumnName(name);
        });
        if (!columns.length) {
            document.getElementById('mysql-query-helper-state').textContent = '请至少勾选一个字段后再填入草稿。';
            return;
        }
        document.querySelector('#' + formId + ' textarea[name="sql"]').value = 'SELECT ' + columns.join(', ') + ' FROM ' + table;
        scheduleDraftSave();
    }

    function stateColumnName(value) {
        return /^[A-Za-z0-9_$]+$/.test(value);
    }

    var mysqlSqlKeywords = {
        select: {value: 'SELECT', label: 'SELECT · 查询开始', spaceAfter: true},
        from: {value: 'FROM', label: 'FROM · 数据来源', spaceAfter: true},
        where: {value: 'WHERE', label: 'WHERE · 过滤条件', spaceAfter: true},
        orderBy: {value: 'ORDER BY', label: 'ORDER BY · 排序', spaceAfter: true},
        and: {value: 'AND', label: 'AND · 并且', spaceAfter: true},
        or: {value: 'OR', label: 'OR · 或者', spaceAfter: true},
        like: {value: 'LIKE', label: 'LIKE · 模式条件', spaceAfter: true},
        in: {value: 'IN', label: 'IN · 集合条件', spaceAfter: true},
        between: {value: 'BETWEEN', label: 'BETWEEN · 区间条件', spaceAfter: true},
        isNull: {value: 'IS NULL', label: 'IS NULL · 空值条件', spaceAfter: true},
        isNotNull: {value: 'IS NOT NULL', label: 'IS NOT NULL · 非空条件', spaceAfter: true},
        asc: {value: 'ASC', label: 'ASC · 升序'},
        desc: {value: 'DESC', label: 'DESC · 降序'}
    };

    function mysqlSqlInputs() {
        return ['mysql-select-sql-input', 'mysql-explain-sql-input'].map(function (id) {
            return document.getElementById(id);
        });
    }

    function mysqlSqlList(input) {
        return document.getElementById(input.getAttribute('aria-controls'));
    }

    function mysqlSqlTokenContext(input) {
        if (input.selectionStart !== input.selectionEnd) {
            return null;
        }
        var caret = input.selectionStart;
        var before = input.value.substring(0, caret);
        // 服务端会拒绝含注释或分号的 SQL，联想在此提前放弃，避免误导用户往会被拒绝的方向补全
        if (/--|\/\*|\*\/|#|;/.test(input.value)) {
            return null;
        }
        var match = /[A-Za-z0-9_$]*$/.exec(before);
        var token = match ? match[0] : '';
        return {before: before, token: token, start: caret - token.length, end: caret};
    }

    function mysqlSqlSourceTable(value) {
        if (/--|\/\*|\*\/|#|;|\b(?:JOIN|UNION|WITH)\b|\./i.test(value)) {
            return null;
        }
        var matches = value.match(/\bFROM\s+([A-Za-z0-9_$]+)\b/ig) || [];
        if (matches.length !== 1) {
            return null;
        }
        var match = /\bFROM\s+([A-Za-z0-9_$]+)\b/i.exec(matches[0]);
        return match ? match[1] : null;
    }

    function mysqlSqlColumnSuggestions(input, context) {
        var state = workspaceStates.mysql;
        var table = mysqlSqlSourceTable(input.value);
        var beforeToken = context.before.substring(0, context.start);
        var projection = /^\s*SELECT\s+[\s\S]*$/i.test(beforeToken) && !/\bFROM\b/i.test(beforeToken);
        var condition = /\b(?:WHERE|AND|OR)\s*$/i.test(beforeToken);
        var ordering = /\bORDER\s+BY\s*$/i.test(beforeToken);
        if (!table && projection) {
            table = state.mysqlColumnTable;
        }
        if (!table || !state.selectedDatasourceKey || state.mysqlColumnDatasourceKey !== state.selectedDatasourceKey
            || state.mysqlColumnTable !== table || !(projection || condition || ordering)) {
            return [];
        }
        return state.mysqlColumnItems.map(function (item) {
            return {value: item.name, label: item.name + ' · 字段候选'};
        });
    }

    function mysqlSqlKeywordSuggestions(context) {
        var before = context.before;
        var beforeToken = before.substring(0, context.start);
        var normalizedBefore = beforeToken.trim();
        if (!normalizedBefore) {
            return [mysqlSqlKeywords.select];
        }
        if (/^\s*SELECT\s+[\s\S]*$/i.test(beforeToken) && !/\bFROM\b/i.test(beforeToken)) {
            return [mysqlSqlKeywords.from];
        }
        if (/\bFROM\s+[A-Za-z0-9_$]+\s*$/i.test(beforeToken)) {
            return [mysqlSqlKeywords.where, mysqlSqlKeywords.orderBy];
        }
        if (/\bORDER\s+BY\s+[A-Za-z0-9_$]+\s*$/i.test(beforeToken)) {
            return [mysqlSqlKeywords.asc, mysqlSqlKeywords.desc];
        }
        if (/\b(?:WHERE|AND|OR)\s+[A-Za-z0-9_$]+\s*$/i.test(beforeToken)) {
            return [mysqlSqlKeywords.like, mysqlSqlKeywords.in, mysqlSqlKeywords.between,
                mysqlSqlKeywords.isNull, mysqlSqlKeywords.isNotNull];
        }
        if (/(?:'[^']*'|\)|NULL)\s*$/i.test(beforeToken)) {
            return [mysqlSqlKeywords.and, mysqlSqlKeywords.or, mysqlSqlKeywords.orderBy];
        }
        return [];
    }

    function mysqlSqlSuggestionValues(input, context) {
        var state = workspaceStates.mysql;
        var values = [];
        var tableContext = /\bFROM\s*[A-Za-z0-9_$]*$/i.test(context.before);
        if (tableContext && state.selectedDatasourceKey === state.mysqlTableDatasourceKey) {
            values = state.mysqlTableItems.map(function (item) {
                return {value: item.name, label: item.name + ' · 表候选'};
            });
        }
        var columns = mysqlSqlColumnSuggestions(input, context);
        var keywords = mysqlSqlKeywordSuggestions(context);
        values = values.concat(columns, keywords).filter(function (item, index, all) {
            return all.findIndex(function (candidate) {
                return candidate.value.toLowerCase() === item.value.toLowerCase();
            }) === index;
        });
        var token = context.token.toLowerCase();
        // 候选列表上限固定为 10，避免字段很多的表把建议框撑到不可用
        return values.filter(function (item) {
            return item.value.toLowerCase().indexOf(token) === 0;
        }).slice(0, 10);
    }

    function closeMysqlSqlSuggestions() {
        var state = workspaceStates.mysql;
        state.mysqlSqlSuggestionItems = [];
        state.mysqlSqlSuggestionIndex = -1;
        state.mysqlSqlSuggestionInputId = null;
        mysqlSqlInputs().forEach(function (input) {
            var list = mysqlSqlList(input);
            list.hidden = true;
            list.textContent = '';
            input.setAttribute('aria-expanded', 'false');
            input.removeAttribute('aria-activedescendant');
        });
    }

    function renderMysqlSqlSuggestions(input) {
        var context = mysqlSqlTokenContext(input);
        closeMysqlSqlSuggestions();
        if (!context || input !== document.activeElement) {
            return;
        }
        var items = mysqlSqlSuggestionValues(input, context);
        if (!items.length) {
            return;
        }
        var state = workspaceStates.mysql;
        var list = mysqlSqlList(input);
        state.mysqlSqlSuggestionItems = items;
        state.mysqlSqlSuggestionIndex = 0;
        state.mysqlSqlSuggestionInputId = input.id;
        items.forEach(function (item, index) {
            var option = document.createElement('button');
            option.type = 'button';
            option.id = input.id + '-suggestion-' + index;
            option.className = 'ops-sql-suggestion';
            option.setAttribute('role', 'option');
            option.setAttribute('aria-selected', String(index === 0));
            option.textContent = item.label;
            option.title = item.label;
            option.addEventListener('mousedown', function (event) {
                event.preventDefault();
                applyMysqlSqlSuggestion(input, index);
            });
            list.appendChild(option);
        });
        list.hidden = false;
        input.setAttribute('aria-expanded', 'true');
        input.setAttribute('aria-activedescendant', input.id + '-suggestion-0');
    }

    function updateMysqlSqlSuggestionSelection(input) {
        var state = workspaceStates.mysql;
        Array.prototype.forEach.call(mysqlSqlList(input).querySelectorAll('[role="option"]'), function (item, index) {
            item.setAttribute('aria-selected', String(index === state.mysqlSqlSuggestionIndex));
        });
        input.setAttribute('aria-activedescendant', input.id + '-suggestion-' + state.mysqlSqlSuggestionIndex);
    }

    function applyMysqlSqlSuggestion(input, index) {
        var state = workspaceStates.mysql;
        var item = state.mysqlSqlSuggestionItems[index];
        var context = mysqlSqlTokenContext(input);
        if (!item || !context) {
            return;
        }
        var insertedValue = item.value + (item.spaceAfter ? ' ' : '');
        input.value = input.value.substring(0, context.start) + insertedValue + input.value.substring(context.end);
        input.selectionStart = context.start + insertedValue.length;
        input.selectionEnd = input.selectionStart;
        closeMysqlSqlSuggestions();
        scheduleDraftSave();
        input.focus();
    }

    function handleMysqlSqlKeydown(event) {
        var state = workspaceStates.mysql;
        if (!state.mysqlSqlSuggestionItems.length || state.mysqlSqlSuggestionInputId !== event.currentTarget.id) {
            return;
        }
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            state.mysqlSqlSuggestionIndex = (state.mysqlSqlSuggestionIndex
                    + (event.key === 'ArrowDown' ? 1 : -1) + state.mysqlSqlSuggestionItems.length)
                % state.mysqlSqlSuggestionItems.length;
            updateMysqlSqlSuggestionSelection(event.currentTarget);
        } else if (event.key === 'Enter' || event.key === 'Tab') {
            event.preventDefault();
            applyMysqlSqlSuggestion(event.currentTarget, state.mysqlSqlSuggestionIndex);
        } else if (event.key === 'Escape') {
            event.preventDefault();
            closeMysqlSqlSuggestions();
        }
    }

    function renderElasticsearchIndices(state) {
        var input = document.getElementById('elasticsearch-index-input');
        var options = document.getElementById('elasticsearch-index-options');
        var stateNode = document.getElementById('elasticsearch-index-state');
        options.textContent = '';
        state.indexItems.forEach(function (index) {
            var option = document.createElement('option');
            option.value = index;
            options.appendChild(option);
        });
        input.disabled = !state.selectedDatasourceKey;
        if (state.indexLoading) {
            stateNode.textContent = '正在加载当前数据源的索引候选。';
        } else if (!state.selectedDatasourceKey) {
            stateNode.textContent = '请选择数据源后加载索引候选。';
        } else if (state.indexError) {
            stateNode.textContent = '索引候选加载失败，仍可自由输入索引。';
        } else if (state.indexTruncated) {
            stateNode.textContent = '已加载前 100 个索引候选，仍可自由输入未展示的索引。';
        } else if (state.indexItems.length) {
            stateNode.textContent = '已加载 ' + state.indexItems.length + ' 个索引候选，仍可自由输入。';
        } else {
            stateNode.textContent = '当前数据源暂无索引候选，仍可自由输入索引。';
        }
    }

    function loadElasticsearchIndices(datasourceKey) {
        var state = workspaceStates.elasticsearch;
        cancelIndexRequest(state);
        state.indexDatasourceKey = datasourceKey;
        state.indexItems = [];
        state.indexTruncated = false;
        state.indexError = null;
        state.indexLoading = true;
        renderElasticsearchIndices(state);
        var sequence = state.indexSequence;
        var controller = new AbortController();
        state.indexController = controller;
        request('/elasticsearch/datasources/' + encodeURIComponent(datasourceKey) + '/indices', controller.signal)
            .then(function (data) {
                if (state.indexSequence !== sequence || state.selectedDatasourceKey !== datasourceKey) {
                    return;
                }
                state.indexItems = Array.isArray(data.items) ? data.items : [];
                state.indexTruncated = !!data.truncated;
            }).catch(function (error) {
            if (state.indexSequence === sequence && error.name !== 'AbortError') {
                state.indexItems = [];
                state.indexTruncated = false;
                state.indexError = true;
            }
        }).then(function () {
            if (state.indexSequence === sequence) {
                state.indexLoading = false;
                state.indexController = null;
                renderElasticsearchIndices(state);
            }
        });
    }

    var elasticsearchDslTokens = [
        'query', 'match_all', 'bool', 'must', 'filter', 'should', 'must_not', 'term', 'terms', 'match',
        'match_phrase', 'range', 'exists', 'aggs', 'aggregations', 'field', 'size', 'sort', 'order', 'asc', 'desc'
    ];

    function dslTokenContext(input) {
        var caret = input.selectionStart;
        var before = input.value.substring(0, caret);
        var match = /[A-Za-z0-9_.-]*$/.exec(before);
        var token = match ? match[0] : '';
        var start = caret - token.length;
        var propertyKey = /(?:^|[,{])\s*"[A-Za-z0-9_.-]*$/.test(before);
        var fieldValue = /"field"\s*:\s*"[A-Za-z0-9_.-]*$/.test(before);
        var queryField = /"(?:term|terms|match|match_phrase|range)"\s*:\s*\{[\s\S]*?"[A-Za-z0-9_.-]*$/.test(before);
        var existsField = /"exists"\s*:\s*\{\s*"field"\s*:\s*"[A-Za-z0-9_.-]*$/.test(before);
        var sortField = /"sort"\s*:\s*\[[\s\S]*?"[A-Za-z0-9_.-]*$/.test(before);
        return {
            token: token, start: start, end: caret, field: fieldValue || queryField || existsField || sortField,
            structural: propertyKey
        };
    }

    function elasticsearchFieldSuggestion(item) {
        var types = Array.isArray(item.types) && item.types.length ? item.types.join('/') : '未知类型';
        return {
            value: item.name,
            label: item.name + ' · ' + types + (item.aggregatable ? ' · 可聚合' : '')
        };
    }

    function dslSuggestionValues(context) {
        var values = context.field ? workspaceStates.elasticsearch.fieldItems.map(elasticsearchFieldSuggestion)
            : context.structural ? elasticsearchDslTokens.map(function (token) {
                return {value: token, label: token};
            }) : [];
        var normalized = context.token.toLowerCase();
        return values.filter(function (item) {
            return item.value.toLowerCase().indexOf(normalized) === 0;
        }).slice(0, 10);
    }

    function closeElasticsearchDslSuggestions() {
        var input = document.getElementById('elasticsearch-dsl-input');
        var list = document.getElementById('elasticsearch-dsl-suggestions');
        var state = workspaceStates.elasticsearch;
        state.dslSuggestionItems = [];
        state.dslSuggestionIndex = -1;
        list.hidden = true;
        list.textContent = '';
        input.setAttribute('aria-expanded', 'false');
        input.removeAttribute('aria-activedescendant');
    }

    function renderElasticsearchDslSuggestions() {
        var input = document.getElementById('elasticsearch-dsl-input');
        var list = document.getElementById('elasticsearch-dsl-suggestions');
        var state = workspaceStates.elasticsearch;
        var context = dslTokenContext(input);
        var items = dslSuggestionValues(context);
        closeElasticsearchDslSuggestions();
        if (!items.length || input !== document.activeElement) {
            return;
        }
        state.dslSuggestionItems = items;
        state.dslSuggestionIndex = 0;
        list.textContent = '';
        items.forEach(function (item, index) {
            var option = document.createElement('button');
            option.type = 'button';
            option.id = 'elasticsearch-dsl-suggestion-' + index;
            option.className = 'ops-dsl-suggestion';
            option.setAttribute('role', 'option');
            option.setAttribute('aria-selected', index === 0 ? 'true' : 'false');
            option.textContent = item.label;
            option.title = item.label;
            option.addEventListener('mousedown', function (event) {
                event.preventDefault();
                applyElasticsearchDslSuggestion(index);
            });
            list.appendChild(option);
        });
        list.hidden = false;
        input.setAttribute('aria-expanded', 'true');
        input.setAttribute('aria-activedescendant', 'elasticsearch-dsl-suggestion-0');
    }

    function updateElasticsearchDslSuggestionSelection() {
        var input = document.getElementById('elasticsearch-dsl-input');
        var state = workspaceStates.elasticsearch;
        Array.prototype.forEach.call(document.querySelectorAll('#elasticsearch-dsl-suggestions [role="option"]'), function (item, index) {
            item.setAttribute('aria-selected', String(index === state.dslSuggestionIndex));
        });
        input.setAttribute('aria-activedescendant', 'elasticsearch-dsl-suggestion-' + state.dslSuggestionIndex);
    }

    function applyElasticsearchDslSuggestion(index) {
        var input = document.getElementById('elasticsearch-dsl-input');
        var state = workspaceStates.elasticsearch;
        var item = state.dslSuggestionItems[index];
        if (!item) {
            return;
        }
        var context = dslTokenContext(input);
        input.value = input.value.substring(0, context.start) + item.value + input.value.substring(context.end);
        input.selectionStart = context.start + item.value.length;
        input.selectionEnd = input.selectionStart;
        closeElasticsearchDslSuggestions();
        scheduleDraftSave();
        input.focus();
    }

    function handleElasticsearchDslKeydown(event) {
        var state = workspaceStates.elasticsearch;
        if (!state.dslSuggestionItems.length) {
            return;
        }
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            state.dslSuggestionIndex = (state.dslSuggestionIndex + (event.key === 'ArrowDown' ? 1 : -1)
                + state.dslSuggestionItems.length) % state.dslSuggestionItems.length;
            updateElasticsearchDslSuggestionSelection();
        } else if (event.key === 'Enter' || event.key === 'Tab') {
            event.preventDefault();
            applyElasticsearchDslSuggestion(state.dslSuggestionIndex);
        } else if (event.key === 'Escape') {
            event.preventDefault();
            closeElasticsearchDslSuggestions();
        }
    }

    function renderElasticsearchDocuments(state, message) {
        var stateNode = document.getElementById('elasticsearch-console-result-state');
        var output = document.getElementById('elasticsearch-console-response-output');
        if (message) {
            stateNode.textContent = message;
            output.value = '';
        } else if (state.documentLoading) {
            stateNode.textContent = '正在查询。';
            output.value = '';
        } else if (state.documentError) {
            stateNode.textContent = state.documentError;
            output.value = '';
        } else if (state.documentData) {
            stateNode.textContent = '已返回 Elasticsearch 响应。';
            output.value = JSON.stringify(state.documentData, null, 2);
        } else {
            stateNode.textContent = '等待查询。';
            output.value = '';
        }
    }

    function initializeElasticsearchWorkbenchSplitter() {
        var workbench = document.getElementById('elasticsearch-console-workbench');
        var splitter = document.getElementById('elasticsearch-console-splitter');
        var min = 30;
        var max = 70;
        var step = 5;

        function isSplitLayout() {
            return window.getComputedStyle(splitter).display !== 'none';
        }

        function setRequestPaneWidth(value) {
            var width = Math.max(min, Math.min(max, value));
            workbench.style.setProperty('--elasticsearch-request-pane-width', width + '%');
            splitter.setAttribute('aria-valuenow', String(width));
            splitter.setAttribute('aria-valuetext', '请求区域占工作台宽度 ' + width + '%');
        }

        function setRequestPaneWidthFromPointer(event) {
            var bounds = workbench.getBoundingClientRect();
            if (bounds.width > 0) {
                setRequestPaneWidth((event.clientX - bounds.left) * 100 / bounds.width);
            }
        }

        splitter.addEventListener('pointerdown', function (event) {
            if (!isSplitLayout()) {
                return;
            }
            event.preventDefault();
            splitter.setPointerCapture(event.pointerId);
            setRequestPaneWidthFromPointer(event);
        });
        splitter.addEventListener('pointermove', function (event) {
            if (splitter.hasPointerCapture(event.pointerId) && isSplitLayout()) {
                setRequestPaneWidthFromPointer(event);
            }
        });
        splitter.addEventListener('pointerup', function (event) {
            if (splitter.hasPointerCapture(event.pointerId)) {
                splitter.releasePointerCapture(event.pointerId);
            }
        });
        splitter.addEventListener('pointercancel', function (event) {
            if (splitter.hasPointerCapture(event.pointerId)) {
                splitter.releasePointerCapture(event.pointerId);
            }
        });
        splitter.addEventListener('keydown', function (event) {
            if (!isSplitLayout()) {
                return;
            }
            var current = Number(splitter.getAttribute('aria-valuenow'));
            if (event.key === 'ArrowLeft') {
                event.preventDefault();
                setRequestPaneWidth(current - step);
            } else if (event.key === 'ArrowRight') {
                event.preventDefault();
                setRequestPaneWidth(current + step);
            } else if (event.key === 'Home') {
                event.preventDefault();
                setRequestPaneWidth(min);
            } else if (event.key === 'End') {
                event.preventDefault();
                setRequestPaneWidth(max);
            }
        });
    }

    function loadElasticsearchDocuments(datasourceKey, index, dsl) {
        var state = workspaceStates.elasticsearch;
        cancelDocumentRequest(state);
        state.documentDatasourceKey = datasourceKey;
        state.documentData = null;
        state.documentError = null;
        state.documentLoading = true;
        renderElasticsearchDocuments(state);
        var sequence = state.documentSequence;
        var controller = new AbortController();
        state.documentController = controller;
        request('/elasticsearch/datasources/' + encodeURIComponent(datasourceKey) + '/documents?'
            + queryString({index: index, dsl: encodeBase64Url(dsl)}), controller.signal)
            .then(function (data) {
                if (state.documentSequence !== sequence || state.selectedDatasourceKey !== datasourceKey) {
                    return;
                }
                state.documentData = data;
            }).catch(function (error) {
            if (state.documentSequence === sequence && error.name !== 'AbortError') {
                state.documentError = error.message;
            }
        }).then(function () {
            if (state.documentSequence === sequence) {
                state.documentLoading = false;
                state.documentController = null;
                renderElasticsearchDocuments(state);
            }
        });
    }

    function clearAuditBody(workspace) {
        document.getElementById(workspace + '-audit-body').textContent = '';
    }

    function auditControls(workspace) {
        return {
            range: document.getElementById(workspace + '-audit-range'),
            from: document.getElementById(workspace + '-audit-from'),
            to: document.getElementById(workspace + '-audit-to'),
            fromLabel: document.getElementById(workspace + '-audit-from-label'),
            toLabel: document.getElementById(workspace + '-audit-to-label'),
            query: document.getElementById(workspace + '-audit-query'),
            effective: document.getElementById(workspace + '-audit-effective'),
            previous: document.getElementById(workspace + '-audit-previous'),
            next: document.getElementById(workspace + '-audit-next')
        };
    }

    function setAuditLoading(workspace, loading) {
        var controls = auditControls(workspace);
        controls.range.disabled = loading;
        controls.from.disabled = loading;
        controls.to.disabled = loading;
        controls.query.disabled = loading;
        var state = workspaceStates[workspace];
        controls.previous.disabled = loading || state.auditPage <= 1;
        controls.next.disabled = loading || !state.auditHasMore;
    }

    function cancelAuditRequest(state) {
        state.auditSequence++;
        if (state.auditController) {
            state.auditController.abort();
            state.auditController = null;
        }
    }

    function auditContext(workspace, item) {
        var values;
        if (workspace === 'elasticsearch') {
            values = [['ES 索引', item.elasticsearchIndex], ['ES DSL', item.elasticsearchDsl],
                ['页码', item.page], ['数量', item.size]];
        } else if (workspace === 'redis') {
            values = [['Redis Key', item.redisKey], ['Redis Field', item.redisField],
                ['数量', item.size], ['偏移量', item.offset]];
        } else if (workspace === 'kafka') {
            values = [['Kafka Topic', item.kafkaTopic], ['Kafka Group', item.kafkaGroupId], ['数量', item.size]];
        } else {
            values = [['MySQL SQL', item.mysqlSql], ['数量', item.size]];
        }
        return values.filter(function (entry) {
            return entry[1] !== null && entry[1] !== undefined && entry[1] !== '';
        }).map(function (entry) {
            return entry[0] + '=' + entry[1];
        }).join('；');
    }

    function auditTimeZoneLabel() {
        return auditTimeZone === 'utc' ? 'UTC' : '北京时间';
    }

    function auditTimeZoneOffset() {
        return auditTimeZone === 'utc' ? 0 : 8 * 60 * 60 * 1000;
    }

    function padDateTimePart(value) {
        return String(value).padStart(2, '0');
    }

    function dateTimeParts(value) {
        var match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value || '');
        if (!match) {
            return null;
        }
        return {
            year: Number(match[1]),
            month: Number(match[2]),
            day: Number(match[3]),
            hour: Number(match[4]),
            minute: Number(match[5]),
            second: Number(match[6] || '00')
        };
    }

    function dateFromWallClock(value, timeZone) {
        var parts = dateTimeParts(value);
        if (!parts) {
            return null;
        }
        var timestamp = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second);
        var date = new Date(timestamp);
        if (date.getUTCFullYear() !== parts.year || date.getUTCMonth() !== parts.month - 1
            || date.getUTCDate() !== parts.day || date.getUTCHours() !== parts.hour
            || date.getUTCMinutes() !== parts.minute || date.getUTCSeconds() !== parts.second) {
            return null;
        }
        return new Date(timestamp - (timeZone === 'utc' ? 0 : 8 * 60 * 60 * 1000));
    }

    function auditInstant(value) {
        var plainDateTime = dateFromWallClock(value, 'utc');
        if (plainDateTime) {
            return plainDateTime;
        }
        var timestamp = Date.parse(value);
        return isNaN(timestamp) ? null : new Date(timestamp);
    }

    function utcDateTime(value) {
        var date = dateFromWallClock(value, auditTimeZone);
        if (!date) {
            return null;
        }
        return date.getUTCFullYear() + '-' + padDateTimePart(date.getUTCMonth() + 1) + '-'
            + padDateTimePart(date.getUTCDate()) + 'T' + padDateTimePart(date.getUTCHours()) + ':'
            + padDateTimePart(date.getUTCMinutes()) + ':' + padDateTimePart(date.getUTCSeconds());
    }

    function auditInputDateTime(value, timeZone) {
        var date = value instanceof Date ? value : auditInstant(value);
        if (!date) {
            return value;
        }
        var displayDate = new Date(date.getTime() + (timeZone === 'utc' ? 0 : 8 * 60 * 60 * 1000));
        return displayDate.getUTCFullYear() + '-' + padDateTimePart(displayDate.getUTCMonth() + 1) + '-'
            + padDateTimePart(displayDate.getUTCDate()) + 'T' + padDateTimePart(displayDate.getUTCHours()) + ':'
            + padDateTimePart(displayDate.getUTCMinutes()) + ':' + padDateTimePart(displayDate.getUTCSeconds());
    }

    function formatAuditDateTime(value, inputValue) {
        var date = value instanceof Date ? value : auditInstant(value);
        if (!date) {
            return value;
        }
        if (inputValue) {
            return auditInputDateTime(date, auditTimeZone);
        }
        return new Intl.DateTimeFormat('zh-CN', {
            timeZone: auditTimeZone === 'utc' ? 'UTC' : 'Asia/Shanghai',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hourCycle: 'h23'
        }).format(date);
    }

    function effectiveAuditRange(data) {
        if (!data.from || !data.to) {
            return '未返回有效时间范围。';
        }
        return '实际查询范围（' + auditTimeZoneLabel() + '）：' + formatAuditDateTime(data.from)
            + ' 至 ' + formatAuditDateTime(data.to);
    }

    function auditOperation(capability) {
        var names = {
            ELASTICSEARCH_DATASOURCE_CATALOG: '读取数据源目录',
            ELASTICSEARCH_SUMMARY: '读取集群摘要',
            ELASTICSEARCH_INDEX_LIST: '读取索引候选',
            ELASTICSEARCH_DOCUMENT_QUERY: '查询文档',
            REDIS_DATASOURCE_CATALOG: '读取数据源目录',
            REDIS_DATASOURCE_LIST: '读取数据源摘要',
            REDIS_SUMMARY: '读取数据源摘要',
            REDIS_KEY_METADATA: '读取 Key 元数据',
            REDIS_KEY_READ: '读取 Key 数据',
            KAFKA_DATASOURCE_CATALOG: '读取数据源目录',
            KAFKA_DATASOURCE_LIST: '读取数据源诊断',
            KAFKA_TOPIC_LIST: '读取 Topic 清单',
            KAFKA_TOPIC_CONFIG: '读取 Topic 固定配置',
            KAFKA_TOPIC_RUNTIME: '读取 Topic 运行态',
            KAFKA_CONSUMER_GROUP_LIST: '读取消费组清单',
            KAFKA_CONSUMER_GROUP_DETAIL: '读取消费组详情',
            KAFKA_CONSUMER_GROUP_LAG_LIST: '读取消费组积压',
            MYSQL_DATASOURCE_CATALOG: '读取数据源目录',
            MYSQL_DATASOURCE_STATUS: '探测数据源状态',
            MYSQL_SELECT: '执行受控查询',
            MYSQL_EXPLAIN: '执行受控 Explain',
            MYSQL_TABLE_LIST: '读取表和视图目录',
            MYSQL_TABLE_COLUMNS: '读取列目录',
            MYSQL_TABLE_INDEXES: '读取索引目录'
        };
        return names[capability] || '受限读取操作';
    }

    function formatAuditTime(value) {
        return formatAuditDateTime(value);
    }

    function renderAuditItems(workspace, items) {
        var body = document.getElementById(workspace + '-audit-body');
        clearAuditBody(workspace);
        items.forEach(function (item) {
            var row = document.createElement('tr');
            var values = [formatAuditTime(item.occurredAt), item.subject, auditOperation(item.capability),
                item.datasourceKey, tagText(item.clusterTag), item.httpStatus, auditContext(workspace, item),
                item.durationMillis == null ? null : item.durationMillis + ' ms'];
            values.forEach(function (value) {
                var cell = document.createElement('td');
                cell.textContent = value == null || value === '' ? '-' : value;
                cell.title = cell.textContent;
                row.appendChild(cell);
            });
            body.appendChild(row);
        });
    }

    function renderAudit(workspace, data) {
        var state = workspaceStates[workspace];
        var stateNode = document.getElementById(workspace + '-audit-state');
        var controls = auditControls(workspace);
        var items = data.items || [];
        state.auditData = data;
        state.auditPage = Number(data.page) > 0 ? Number(data.page) : state.auditPage;
        state.auditFrom = data.from || null;
        state.auditTo = data.to || null;
        state.auditHasMore = !!data.hasMore;
        state.auditLoaded = true;
        state.auditPending = false;
        state.auditPendingRange = '';
        state.auditPendingFrom = null;
        state.auditPendingTo = null;
        state.auditPendingSnapshot = null;
        controls.effective.textContent = effectiveAuditRange(data);
        document.getElementById(workspace + '-audit-page').textContent = '第 ' + state.auditPage + ' 页';
        controls.previous.disabled = state.auditPage <= 1;
        controls.next.disabled = !state.auditHasMore;
        if (!items.length) {
            clearAuditBody(workspace);
            stateNode.textContent = state.auditPage === 1 ? '暂无可读取审计记录。' : '当前页没有审计记录。';
            return;
        }
        stateNode.textContent = '已展示 ' + items.length + ' 条脱敏审计记录。';
        renderAuditItems(workspace, items);
    }

    function refreshAuditTimeZoneLabels(workspace) {
        var controls = auditControls(workspace);
        controls.fromLabel.textContent = '开始时间（' + auditTimeZoneLabel() + '）';
        controls.toLabel.textContent = '结束时间（' + auditTimeZoneLabel() + '）';
    }

    function refreshAuditTimeZoneDisplay() {
        workspaceNames.forEach(function (workspace) {
            var state = workspaceStates[workspace];
            var controls = auditControls(workspace);
            refreshAuditTimeZoneLabels(workspace);
            if (state.auditData && !state.auditLoading) {
                controls.effective.textContent = effectiveAuditRange(state.auditData);
                renderAuditItems(workspace, state.auditData.items || []);
            }
        });
    }

    function restoreAuditTimeZone() {
        var storedValue;
        try {
            storedValue = window.localStorage.getItem(auditTimeZoneStorageKey);
        } catch (error) {
            storedValue = null;
        }
        auditTimeZone = storedValue === 'utc' ? 'utc' : 'beijing';
        auditTimeZoneSelect.value = auditTimeZone;
        refreshAuditTimeZoneDisplay();
    }

    function setAuditTimeZone(value) {
        var previousTimeZone = auditTimeZone;
        var customInputs = {};
        workspaceNames.forEach(function (workspace) {
            var controls = auditControls(workspace);
            if (controls.range.value === 'custom') {
                customInputs[workspace] = {
                    from: dateFromWallClock(controls.from.value, previousTimeZone),
                    to: dateFromWallClock(controls.to.value, previousTimeZone)
                };
            }
        });
        auditTimeZone = value === 'utc' ? 'utc' : 'beijing';
        auditTimeZoneSelect.value = auditTimeZone;
        Object.keys(customInputs).forEach(function (workspace) {
            var controls = auditControls(workspace);
            if (customInputs[workspace].from) {
                controls.from.value = auditInputDateTime(customInputs[workspace].from, auditTimeZone);
            }
            if (customInputs[workspace].to) {
                controls.to.value = auditInputDateTime(customInputs[workspace].to, auditTimeZone);
            }
        });
        try {
            window.localStorage.setItem(auditTimeZoneStorageKey, auditTimeZone);
        } catch (error) {
            // 无法保存时仍保持当前页面展示设置。
        }
        refreshAuditTimeZoneDisplay();
    }

    function restoreAuditSnapshot(workspace, snapshot) {
        var state = workspaceStates[workspace];
        state.auditPending = false;
        state.auditPendingRange = '';
        state.auditPendingFrom = null;
        state.auditPendingTo = null;
        state.auditPendingSnapshot = null;
        if (snapshot) {
            state.auditPage = snapshot.page;
            state.auditHasMore = snapshot.hasMore;
            state.auditLoaded = true;
            renderAudit(workspace, snapshot.data);
        }
    }

    function auditSnapshot(state) {
        if (!state.auditData) {
            return null;
        }
        return {data: state.auditData, page: state.auditPage, hasMore: state.auditHasMore};
    }

    function loadAudit(workspace, previousPage) {
        var state = workspaceStates[workspace];
        var snapshot = state.auditPendingSnapshot || auditSnapshot(state);
        var controls = auditControls(workspace);
        state.auditPendingSnapshot = snapshot;
        cancelAuditRequest(state);
        state.auditLoading = true;
        var sequence = state.auditSequence;
        var controller = new AbortController();
        state.auditController = controller;
        var values = {page: state.auditPage, size: 10};
        if (state.auditPending && state.auditPendingFrom && state.auditPendingTo) {
            values.from = state.auditPendingFrom;
            values.to = state.auditPendingTo;
        } else if (state.auditPending && state.auditPendingRange) {
            values.range = state.auditPendingRange;
        } else if (state.auditFrom && state.auditTo) {
            values.from = state.auditFrom;
            values.to = state.auditTo;
        } else if (state.auditRange) {
            values.range = state.auditRange;
        }
        document.getElementById(workspace + '-audit-state').textContent = '正在加载审计记录。';
        clearAuditBody(workspace);
        setAuditLoading(workspace, true);
        request('/audit/' + workspace + '/records?' + queryString(values), controller.signal).then(function (data) {
            if (state.auditSequence === sequence) {
                renderAudit(workspace, data);
            }
        }).catch(function (error) {
            if (state.auditSequence === sequence) {
                restoreAuditSnapshot(workspace, snapshot);
                if (error.name !== 'AbortError') {
                    document.getElementById(workspace + '-audit-state').textContent = error.message;
                }
            }
        }).then(function () {
            if (state.auditSequence === sequence) {
                state.auditLoading = false;
                state.auditController = null;
                setAuditLoading(workspace, false);
            }
        });
    }

    function submitAuditQuery(workspace) {
        var state = workspaceStates[workspace];
        var controls = auditControls(workspace);
        var range = controls.range.value;
        var from;
        var to;
        if (range === 'custom') {
            from = utcDateTime(controls.from.value);
            to = utcDateTime(controls.to.value);
            if (!from || !to || from >= to || auditInstant(to).getTime() - auditInstant(from).getTime()
                > auditMaxRangeDays * 24 * 60 * 60 * 1000) {
                document.getElementById(workspace + '-audit-state').textContent = '自定义时间范围无效，最长不超过 '
                    + auditMaxRangeDays + ' 天。';
                return;
            }
        }
        cancelAuditRequest(state);
        state.auditPendingSnapshot = auditSnapshot(state);
        state.auditRequested = true;
        state.auditPending = true;
        state.auditPendingRange = range === 'custom' ? '' : range;
        state.auditPendingFrom = range === 'custom' ? from : null;
        state.auditPendingTo = range === 'custom' ? to : null;
        state.auditHasMore = false;
        state.auditLoaded = false;
        state.auditPage = 1;
        controls.effective.textContent = '正在确认新的 ' + auditTimeZoneLabel() + ' 查询范围。';
        loadAudit(workspace);
    }

    function parseHash() {
        var parts = window.location.hash.replace(/^#/, '').split('/');
        if (workspaceNames.indexOf(parts[0]) === -1 || sectionNames.indexOf(parts[1]) === -1 || parts.length !== 2) {
            window.location.replace('#elasticsearch/overview');
            return;
        }
        if (activeWorkspace !== parts[0] || activeSection !== parts[1]) {
            var previousState = workspaceStates[activeWorkspace];
            var previousSnapshot = previousState.auditPendingSnapshot;
            cancelAuditRequest(previousState);
            if (activeWorkspace === 'elasticsearch') {
                cancelDocumentRequest(previousState);
                cancelIndexRequest(previousState);
                cancelElasticsearchFieldRequest(previousState);
                previousState.indexDatasourceKey = null;
                previousState.indexItems = [];
                previousState.indexTruncated = false;
                previousState.indexError = null;
                previousState.indexLoading = false;
                previousState.fieldDatasourceKey = null;
                previousState.fieldIndex = null;
                previousState.fieldItems = [];
                previousState.fieldTruncated = false;
                previousState.fieldError = null;
                previousState.fieldLoading = false;
                previousState.documentData = null;
                previousState.documentDatasourceKey = null;
                previousState.documentLoading = false;
                closeElasticsearchDslSuggestions();
                renderElasticsearchIndices(previousState);
                renderElasticsearchFieldState(previousState);
                renderElasticsearchDocuments(previousState, '切换工作区后已清除查询结果。');
            }
            if (activeWorkspace === 'mysql') {
                clearMysqlSuggestions(previousState);
                renderMysqlSuggestions(previousState);
            }
            previousState.auditLoading = false;
            if (previousSnapshot) {
                restoreAuditSnapshot(activeWorkspace, previousSnapshot);
            }
        }
        activeWorkspace = parts[0];
        activeSection = parts[1];
        renderActiveState();
    }

    function discardOtherConsoleResults(workspace) {
        workspaceNames.forEach(function (current) {
            if (current !== workspace) {
                Array.prototype.forEach.call(workspacePanel(current).querySelectorAll('.ops-result'), function (result) {
                    setResult(result.id, '切换工作区后已清除查询结果。');
                });
                resetConsolePanel(current);
                if (current === 'kafka') {
                    resetKafkaTopicConsoleState('切换工作区后已清除查询状态。');
                }
            }
        });
        if (workspace !== 'elasticsearch') {
            var state = workspaceStates.elasticsearch;
            cancelDocumentRequest(state);
            state.documentData = null;
            state.documentDatasourceKey = null;
            state.documentError = null;
            state.documentLoading = false;
            renderElasticsearchDocuments(state, '切换工作区后已清除查询结果。');
        }
    }

    function renderActiveState() {
        var activePanel = workspacePanel(activeWorkspace);
        var workspaceText = {elasticsearch: 'Elasticsearch', redis: 'Redis', kafka: 'Kafka', mysql: 'MySQL'};
        var sectionText = {overview: '概览', console: '控制台', audit: '审计'};
        var descriptions = {
            overview: '选择数据源并查看当前工作区的安全状态。',
            console: '使用受限操作查询运行状态。',
            audit: '查看操作记录。'
        };
        Array.prototype.forEach.call(activePanel.querySelectorAll('[data-workspace-title]'), function (item) {
            item.textContent = workspaceText[activeWorkspace] + ' ' + sectionText[activeSection];
        });
        Array.prototype.forEach.call(activePanel.querySelectorAll('[data-workspace-breadcrumb]'), function (item) {
            item.textContent = workspaceText[activeWorkspace] + ' / ' + sectionText[activeSection];
        });
        Array.prototype.forEach.call(activePanel.querySelectorAll('[data-workspace-description]'), function (item) {
            item.textContent = descriptions[activeSection];
        });
        Array.prototype.forEach.call(document.querySelectorAll('.ops-workspace-nav'), function (item) {
            item.classList.toggle('is-active', item.getAttribute('data-workspace') === activeWorkspace);
        });
        Array.prototype.forEach.call(document.querySelectorAll('.ops-workspace'), function (panel) {
            panel.classList.toggle('is-active', panel.getAttribute('data-workspace-panel') === activeWorkspace);
        });
        Array.prototype.forEach.call(activePanel.querySelectorAll('.ops-section-tab'), function (item) {
            item.classList.toggle('is-active', item.getAttribute('data-section') === activeSection);
        });
        Array.prototype.forEach.call(activePanel.querySelectorAll('.ops-section'), function (panel) {
            panel.classList.toggle('is-active', panel.getAttribute('data-section-panel') === activeSection);
        });
        discardOtherConsoleResults(activeWorkspace);
        loadCatalog(activeWorkspace);
        if (activeWorkspace === 'elasticsearch') {
            var elasticsearchState = workspaceStates.elasticsearch;
            var elasticsearchDatasource = selectedDatasource('elasticsearch');
            if (elasticsearchDatasource && elasticsearchState.indexDatasourceKey !== elasticsearchDatasource.datasourceKey) {
                loadElasticsearchIndices(elasticsearchDatasource.datasourceKey);
            }
            updateElasticsearchFieldCapabilities();
        }
        if (activeSection === 'audit' && !workspaceStates[activeWorkspace].auditRequested
            && !workspaceStates[activeWorkspace].auditLoading) {
            workspaceStates[activeWorkspace].auditRequested = true;
            loadAudit(activeWorkspace);
        }
    }

    Array.prototype.forEach.call(document.querySelectorAll('.ops-workspace-nav'), function (item) {
        item.addEventListener('click', function () {
            window.location.hash = item.getAttribute('data-workspace') + '/overview';
        });
    });

    Array.prototype.forEach.call(document.querySelectorAll('.ops-section-tab'), function (item) {
        item.addEventListener('click', function () {
            var workspace = item.closest('.ops-workspace').getAttribute('data-workspace-panel');
            window.location.hash = workspace + '/' + item.getAttribute('data-section');
        });
    });

    Array.prototype.forEach.call(document.querySelectorAll('[data-console-panel-toggle]'), function (toggle) {
        toggle.addEventListener('click', function () {
            var workspace = toggle.closest('.ops-workspace').getAttribute('data-workspace-panel');
            setConsolePanelExpanded(workspace, toggle.getAttribute('data-console-panel-toggle'));
        });
    });

    workspaceNames.forEach(function (workspace) {
        Array.prototype.forEach.call(workspaceSelects(workspace), function (select) {
            select.addEventListener('change', function () {
                setSelectedDatasource(workspace, select.value);
            });
        });
    });

    draftFields().forEach(function (field) {
        field.addEventListener('input', scheduleDraftSave);
        field.addEventListener('change', scheduleDraftSave);
    });

    document.getElementById('ops-clear-drafts').addEventListener('click', function () {
        clearConsoleDrafts();
        Array.prototype.forEach.call(document.querySelectorAll('.ops-workspace .ops-section[data-section-panel="console"] form'),
            function (form) {
                form.reset();
            });
        workspaceNames.forEach(function (workspace) {
            var state = workspaceStates[workspace];
            state.selectedDatasourceKey = null;
            clearWorkspaceResults(workspace);
            if (workspace === 'elasticsearch') {
                closeElasticsearchDslSuggestions();
                renderElasticsearchFieldState(state);
            }
            if (state.catalog) {
                renderCatalog(workspace, state.catalog);
            }
        });
        renderActiveState();
    });

    document.getElementById('ops-logout-form').addEventListener('submit', clearConsoleDrafts);

    document.getElementById('redis-datasource-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        setResult('redis-datasource-console-result', '正在查询数据源摘要。');
        request('/redis/datasources').then(function (data) {
            setResult('redis-datasource-console-result', data);
        }).catch(function (error) {
            setResult('redis-datasource-console-result', error.message);
        });
    });

    document.getElementById('redis-summary-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('redis');
        } catch (error) {
            setResult('redis-summary-console-result', error.message);
            return;
        }
        setResult('redis-summary-console-result', '正在查询数据源摘要。');
        request('/redis/datasources/' + encodeURIComponent(datasourceKey) + '/summary')
            .then(function (data) {
                setResult('redis-summary-console-result', data);
            })
            .catch(function (error) {
                setResult('redis-summary-console-result', error.message);
            });
    });

    document.getElementById('kafka-datasource-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        setResult('kafka-datasource-console-result', '正在查询数据源诊断。');
        request('/kafka/datasources').then(function (data) {
            setResult('kafka-datasource-console-result', data);
        }).catch(function (error) {
            setResult('kafka-datasource-console-result', error.message);
        });
    });

    document.getElementById('kafka-topic-list-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('kafka');
        } catch (error) {
            setResult('kafka-topic-list-console-result', error.message);
            return;
        }
        setResult('kafka-topic-list-console-result', '正在查询 Topic 清单。');
        request('/kafka/datasources/' + encodeURIComponent(datasourceKey) + '/topics?'
            + queryString({prefix: form.get('prefix'), size: form.get('size')})).then(function (data) {
            setPagedResult('kafka-topic-list-console-result', data);
        }).catch(function (error) {
            setResult('kafka-topic-list-console-result', error.message);
        });
    });

    document.getElementById('kafka-group-list-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('kafka');
        } catch (error) {
            setResult('kafka-group-list-console-result', error.message);
            return;
        }
        setResult('kafka-group-list-console-result', '正在查询消费组清单。');
        request('/kafka/datasources/' + encodeURIComponent(datasourceKey) + '/consumer-groups?'
            + queryString({prefix: form.get('prefix'), size: form.get('size')})).then(function (data) {
            setPagedResult('kafka-group-list-console-result', data);
        }).catch(function (error) {
            setResult('kafka-group-list-console-result', error.message);
        });
    });

    document.getElementById('kafka-topic-config-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('kafka');
        } catch (error) {
            setResult('kafka-topic-config-console-result', error.message);
            return;
        }
        setResult('kafka-topic-config-console-result', '正在查询 Topic 固定配置。');
        request('/kafka/datasources/' + encodeURIComponent(datasourceKey) + '/topics/config?'
            + queryString({topic: form.get('topic')})).then(function (data) {
            setResult('kafka-topic-config-console-result', data);
        }).catch(function (error) {
            setResult('kafka-topic-config-console-result', error.message);
        });
    });

    document.getElementById('kafka-group-detail-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('kafka');
        } catch (error) {
            setResult('kafka-group-detail-console-result', error.message);
            return;
        }
        setResult('kafka-group-detail-console-result', '正在查询消费组详情。');
        request('/kafka/datasources/' + encodeURIComponent(datasourceKey) + '/consumer-groups/detail?'
            + queryString({groupId: form.get('groupId')})).then(function (data) {
            setResult('kafka-group-detail-console-result', data);
        }).catch(function (error) {
            setResult('kafka-group-detail-console-result', error.message);
        });
    });

    document.getElementById('elasticsearch-index-input').addEventListener('input', function () {
        scheduleDraftSave();
        updateElasticsearchFieldCapabilities();
    });

    document.getElementById('elasticsearch-dsl-input').addEventListener('input', function () {
        scheduleDraftSave();
        renderElasticsearchDslSuggestions();
    });

    document.getElementById('elasticsearch-dsl-input').addEventListener('keydown', handleElasticsearchDslKeydown);
    document.getElementById('elasticsearch-dsl-input').addEventListener('click', renderElasticsearchDslSuggestions);
    document.getElementById('elasticsearch-dsl-input').addEventListener('blur', function () {
        window.setTimeout(closeElasticsearchDslSuggestions, 100);
    });

    document.getElementById('elasticsearch-format-dsl').addEventListener('click', function () {
        var input = document.getElementById('elasticsearch-dsl-input');
        try {
            input.value = JSON.stringify(JSON.parse(input.value), null, 2);
            scheduleDraftSave();
            document.getElementById('elasticsearch-console-result-state').textContent = 'JSON 格式有效，索引或通配模式提交后仍由服务端校验。';
        } catch (error) {
            document.getElementById('elasticsearch-console-result-state').textContent = 'JSON 格式无效。';
        }
    });

    document.getElementById('mysql-select-all-columns').addEventListener('click', function () {
        var state = workspaceStates.mysql;
        if (!state.mysqlColumnItems.length || !state.mysqlColumnTable) {
            return;
        }
        var allSelected = state.mysqlSelectedColumnNames.length === state.mysqlColumnItems.length;
        state.mysqlSelectedColumnNames = allSelected ? [] : state.mysqlColumnItems.map(function (item) {
            return item.name;
        });
        renderMysqlSuggestions(state);
        scheduleDraftSave();
    });

    mysqlSqlInputs().forEach(function (input) {
        input.addEventListener('input', function () {
            renderMysqlSqlSuggestions(input);
        });
        input.addEventListener('keydown', handleMysqlSqlKeydown);
        input.addEventListener('click', function () {
            renderMysqlSqlSuggestions(input);
        });
        input.addEventListener('blur', function () {
            // 延迟关闭，让候选按钮的 mousedown（先于 blur 触发）能先完成候选填入
            window.setTimeout(closeMysqlSqlSuggestions, 100);
        });
    });

    document.getElementById('mysql-load-table-suggestions').addEventListener('click', function () {
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('mysql');
        } catch (error) {
            document.getElementById('mysql-query-helper-state').textContent = error.message;
            return;
        }
        loadMysqlTableSuggestions(datasourceKey);
    });

    document.getElementById('mysql-query-table-input').addEventListener('input', function () {
        var state = workspaceStates.mysql;
        if ((state.mysqlColumnTable || state.mysqlSelectedColumnTable)
            && state.mysqlSelectedColumnTable !== this.value.trim()) {
            clearMysqlColumnSuggestions(state);
            renderMysqlSuggestions(state);
        }
    });

    document.getElementById('mysql-load-column-suggestions').addEventListener('click', function () {
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('mysql');
        } catch (error) {
            document.getElementById('mysql-query-helper-state').textContent = error.message;
            return;
        }
        var table = document.getElementById('mysql-query-table-input').value.trim();
        if (!isMysqlTableName(table)) {
            document.getElementById('mysql-query-helper-state').textContent = '请先选择或输入仅含字母、数字、下划线或 $ 的精确表名。';
            return;
        }
        loadMysqlColumnSuggestions(datasourceKey, table);
    });

    document.getElementById('mysql-insert-select-draft').addEventListener('click', function () {
        insertMysqlDraft('mysql-select-console-form');
    });

    document.getElementById('mysql-insert-explain-draft').addEventListener('click', function () {
        insertMysqlDraft('mysql-explain-console-form');
    });

    function clearElasticsearchDocuments(message) {
        var state = workspaceStates.elasticsearch;
        cancelDocumentRequest(state);
        state.documentData = null;
        state.documentDatasourceKey = null;
        state.documentError = null;
        state.documentLoading = false;
        renderElasticsearchDocuments(state, message);
    }

    document.getElementById('elasticsearch-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('elasticsearch');
        } catch (error) {
            clearElasticsearchDocuments(error.message);
            return;
        }
        var dsl = form.get('dsl');
        try {
            JSON.parse(dsl);
        } catch (error) {
            clearElasticsearchDocuments('JSON 格式无效。');
            return;
        }
        loadElasticsearchDocuments(datasourceKey, form.get('index'), dsl);
    });

    document.getElementById('redis-key-discovery-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('redis');
        } catch (error) {
            setResult('redis-key-discovery-console-result', error.message);
            return;
        }
        setResult('redis-key-discovery-console-result', '正在发现受限 Key。');
        request('/redis/datasources/' + encodeURIComponent(datasourceKey) + '/keys/discovery?'
            + queryString({prefix: form.get('prefix'), size: form.get('size')})).then(function (data) {
            setPagedResult('redis-key-discovery-console-result', data);
        }).catch(function (error) {
            setResult('redis-key-discovery-console-result', error.message);
        });
    });

    document.getElementById('redis-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('redis');
        } catch (error) {
            setResult('redis-console-result', error.message);
            return;
        }
        var prefix = '/redis/datasources/' + encodeURIComponent(datasourceKey) + '/keys/';
        setResult('redis-console-result', '正在读取元数据。');
        request(prefix + 'metadata?' + queryString({key: form.get('key')})).then(function (metadata) {
            if (!metadata.exists) {
                return {metadata: metadata, value: null};
            }
            setResult('redis-console-result', '已识别 ' + metadata.dataType + '，正在读取受限数据。');
            return request(prefix + 'value?' + queryString({
                key: form.get('key'), field: form.get('field'),
                offset: form.get('offset'), size: form.get('size')
            }))
                .then(function (value) {
                    return {metadata: metadata, value: value};
                });
        }).then(function (data) {
            setPagedResult('redis-console-result', data);
        }).catch(function (error) {
            setResult('redis-console-result', error.message);
        });
    });

    document.getElementById('mysql-status-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('mysql');
        } catch (error) {
            setResult('mysql-status-console-result', error.message);
            return;
        }
        setResult('mysql-status-console-result', '正在探测状态。');
        request('/mysql/datasources/' + encodeURIComponent(datasourceKey) + '/status').then(function (data) {
            setResult('mysql-status-console-result', data);
        }).catch(function (error) {
            setResult('mysql-status-console-result', error.message);
        });
    });

    document.getElementById('mysql-select-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('mysql');
        } catch (error) {
            setResult('mysql-select-console-result', error.message);
            return;
        }
        setResult('mysql-select-console-result', '正在执行受控查询。');
        request('/mysql/datasources/' + encodeURIComponent(datasourceKey) + '/select?'
            + queryString({sql: form.get('sql'), size: form.get('size')})).then(function (data) {
            setPagedResult('mysql-select-console-result', data);
        }).catch(function (error) {
            setResult('mysql-select-console-result', error.message);
        });
    });

    document.getElementById('mysql-explain-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('mysql');
        } catch (error) {
            setResult('mysql-explain-console-result', error.message);
            return;
        }
        setResult('mysql-explain-console-result', '正在执行受控 Explain。');
        request('/mysql/datasources/' + encodeURIComponent(datasourceKey) + '/explain?'
            + queryString({sql: form.get('sql')})).then(function (data) {
            setResult('mysql-explain-console-result', data);
        }).catch(function (error) {
            setResult('mysql-explain-console-result', error.message);
        });
    });

    document.getElementById('mysql-table-list-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('mysql');
        } catch (error) {
            setResult('mysql-table-list-console-result', error.message);
            return;
        }
        setResult('mysql-table-list-console-result', '正在查询表和视图目录。');
        request('/mysql/datasources/' + encodeURIComponent(datasourceKey) + '/tables?'
            + queryString({prefix: form.get('prefix'), size: form.get('size')})).then(function (data) {
            setResult('mysql-table-list-console-result', data);
        }).catch(function (error) {
            setResult('mysql-table-list-console-result', error.message);
        });
    });

    document.getElementById('mysql-table-columns-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('mysql');
        } catch (error) {
            setResult('mysql-table-columns-console-result', error.message);
            return;
        }
        setResult('mysql-table-columns-console-result', '正在查询列目录。');
        request('/mysql/datasources/' + encodeURIComponent(datasourceKey) + '/tables/'
            + encodeURIComponent(form.get('table')) + '/columns').then(function (data) {
            setResult('mysql-table-columns-console-result', data);
        }).catch(function (error) {
            setResult('mysql-table-columns-console-result', error.message);
        });
    });

    document.getElementById('mysql-table-indexes-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('mysql');
        } catch (error) {
            setResult('mysql-table-indexes-console-result', error.message);
            return;
        }
        setResult('mysql-table-indexes-console-result', '正在查询索引目录。');
        request('/mysql/datasources/' + encodeURIComponent(datasourceKey) + '/tables/'
            + encodeURIComponent(form.get('table')) + '/indexes').then(function (data) {
            setResult('mysql-table-indexes-console-result', data);
        }).catch(function (error) {
            setResult('mysql-table-indexes-console-result', error.message);
        });
    });

    document.getElementById('kafka-topic-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('kafka');
        } catch (error) {
            document.getElementById('kafka-topic-console-state').textContent = '无法查询 Topic 运行态。';
            setResult('kafka-topic-console-result', error.message);
            return;
        }
        document.getElementById('kafka-topic-console-state').textContent = '正在查询 Topic 运行态。';
        setResult('kafka-topic-console-result', '正在查询 Topic 运行态。');
        request('/kafka/datasources/' + encodeURIComponent(datasourceKey) + '/topics/runtime?'
            + queryString({topic: form.get('topic')})).then(function (data) {
            document.getElementById('kafka-topic-console-state').textContent = data.truncated
                ? '仅返回服务端上限内的分区，结果已截断。'
                : '已返回全部分区运行态。';
            setPagedResult('kafka-topic-console-result', data);
        }).catch(function (error) {
            document.getElementById('kafka-topic-console-state').textContent = '无法查询 Topic 运行态。';
            setResult('kafka-topic-console-result', error.message);
        });
    });

    document.getElementById('kafka-lag-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('kafka');
        } catch (error) {
            setResult('kafka-lag-console-result', error.message);
            return;
        }
        setResult('kafka-lag-console-result', '正在查询消费组积压。');
        request('/kafka/datasources/' + encodeURIComponent(datasourceKey) + '/consumer-groups/lag?'
            + queryString({groupId: form.get('groupId'), size: 50})).then(function (data) {
            setPagedResult('kafka-lag-console-result', data);
        }).catch(function (error) {
            setResult('kafka-lag-console-result', error.message);
        });
    });

    ['redis-key-discovery-console-result', 'redis-console-result', 'kafka-topic-list-console-result',
        'kafka-group-list-console-result', 'mysql-select-console-result', 'kafka-topic-console-result',
        'kafka-lag-console-result']
        .forEach(initializeResultPagination);

    workspaceNames.forEach(function (workspace) {
        var controls = auditControls(workspace);

        function setAuditCustomVisible() {
            var custom = controls.range.value === 'custom';
            Array.prototype.forEach.call(workspacePanel(workspace).querySelectorAll('.ops-audit-custom'), function (item) {
                item.hidden = !custom;
            });
        }

        controls.range.addEventListener('change', setAuditCustomVisible);
        [controls.from, controls.to].forEach(function (input) {
            input.addEventListener('input', function () {
                if (input.value && controls.range.value !== 'custom') {
                    controls.range.value = 'custom';
                    setAuditCustomVisible();
                }
            });
        });
        controls.query.addEventListener('click', function () {
            submitAuditQuery(workspace);
        });
        controls.previous.addEventListener('click', function () {
            var state = workspaceStates[workspace];
            if (!state.auditLoading && state.auditPage > 1) {
                var previousPage = state.auditPage;
                state.auditPage--;
                loadAudit(workspace, previousPage);
            }
        });
        controls.next.addEventListener('click', function () {
            var state = workspaceStates[workspace];
            if (!state.auditLoading && state.auditHasMore && state.auditFrom && state.auditTo) {
                var previousPage = state.auditPage;
                state.auditPage++;
                loadAudit(workspace, previousPage);
            }
        });
    });

    initializeElasticsearchWorkbenchSplitter();
    restoreAuditTimeZone();
    auditTimeZoneSelect.addEventListener('change', function () {
        setAuditTimeZone(auditTimeZoneSelect.value);
    });
    restoreConsoleDrafts();
    window.addEventListener('hashchange', parseHash);
    parseHash();
}());

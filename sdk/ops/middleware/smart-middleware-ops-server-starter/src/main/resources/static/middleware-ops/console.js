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
    var defaultConsolePanels = {
        elasticsearch: 'query',
        redis: 'key-read',
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
            documentData: null,
            documentDatasourceKey: null,
            documentSelectedIndex: -1,
            documentError: null,
            documentLoading: false,
            documentSequence: 0,
            documentController: null
        };
    });

    function resetKafkaTopicConsoleState(message) {
        document.getElementById('kafka-topic-console-state').textContent = message;
    }

    function clearWorkspaceMemory() {
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
            state.indexDatasourceKey = null;
            state.indexItems = [];
            state.indexTruncated = false;
            state.indexError = null;
            state.indexLoading = false;
            document.getElementById('elasticsearch-index-input').value = '';
            state.documentData = null;
            state.documentDatasourceKey = null;
            state.documentSelectedIndex = -1;
            state.documentError = null;
            state.documentLoading = false;
            resetConsolePanel(workspace);
        });
        document.querySelector('#kafka-topic-console-form input[name="topic"]').value = '';
        document.querySelector('#kafka-lag-console-form input[name="groupId"]').value = '';
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
            document.querySelector('#kafka-topic-console-form input[name="topic"]').value = '';
            document.querySelector('#kafka-lag-console-form input[name="groupId"]').value = '';
            resetKafkaTopicConsoleState('切换数据源后已清除查询状态。');
        }
        if (workspace === 'elasticsearch') {
            cancelIndexRequest(state);
            cancelDocumentRequest(state);
            state.indexDatasourceKey = null;
            state.indexItems = [];
            state.indexTruncated = false;
            state.indexError = null;
            state.indexLoading = false;
            document.getElementById('elasticsearch-index-input').value = '';
            state.documentData = null;
            state.documentDatasourceKey = null;
            state.documentSelectedIndex = -1;
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

    function fillKafkaTopic(topic) {
        document.querySelector('#kafka-topic-console-form input[name="topic"]').value = topic;
        setConsolePanelExpanded('kafka', 'topic-runtime');
    }

    function fillKafkaGroup(groupId) {
        document.querySelector('#kafka-lag-console-form input[name="groupId"]').value = groupId;
        setConsolePanelExpanded('kafka', 'group-lag');
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
        if (workspace === 'elasticsearch' && item) {
            loadElasticsearchIndices(item.datasourceKey);
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

    function renderElasticsearchDocuments(state, message) {
        var stateNode = document.getElementById('elasticsearch-console-result-state');
        var hitsNode = document.getElementById('elasticsearch-console-result-hits');
        var metaNode = document.getElementById('elasticsearch-console-result-document-meta');
        var documentNode = document.getElementById('elasticsearch-console-result-document');
        hitsNode.textContent = '';
        if (message) {
            stateNode.textContent = message;
        } else if (state.documentLoading) {
            stateNode.textContent = '正在查询。';
        } else if (state.documentError) {
            stateNode.textContent = state.documentError;
        } else if (!state.documentData || !state.documentData.items || !state.documentData.items.length) {
            stateNode.textContent = '本次查询没有命中文档。';
        } else {
            stateNode.textContent = '已命中 ' + state.documentData.items.length + ' 条文档。';
            state.documentData.items.forEach(function (hit, index) {
                var button = document.createElement('button');
                button.type = 'button';
                button.className = 'ops-elasticsearch-hit';
                button.classList.toggle('is-selected', index === state.documentSelectedIndex);
                button.textContent = hit.index + ' / ' + hit.id;
                button.title = button.textContent;
                button.addEventListener('click', function () {
                    state.documentSelectedIndex = index;
                    renderElasticsearchDocuments(state);
                });
                hitsNode.appendChild(button);
            });
        }
        var selected = state.documentData && state.documentData.items && state.documentData.items[state.documentSelectedIndex];
        if (selected) {
            metaNode.textContent = selected.index + ' / ' + selected.id;
            documentNode.textContent = JSON.stringify(selected.source || {}, null, 2);
        } else {
            metaNode.textContent = '未选择文档。';
            documentNode.textContent = state.documentLoading ? '正在加载。' : '等待查询。';
        }
        var pager = document.getElementById('elasticsearch-console-result-pagination');
        var hasData = !!state.documentData;
        pager.hidden = !hasData;
        document.getElementById('elasticsearch-console-result-page').textContent = hasData
            ? '第 ' + state.documentData.page + ' 页' : '';
        document.getElementById('elasticsearch-console-result-previous').disabled = !hasData || state.documentData.page <= 1;
        document.getElementById('elasticsearch-console-result-next').disabled = !hasData || !state.documentData.hasMore;
    }

    function loadElasticsearchDocuments(datasourceKey, index, dsl, page) {
        var state = workspaceStates.elasticsearch;
        cancelDocumentRequest(state);
        state.documentDatasourceKey = datasourceKey;
        state.documentData = null;
        state.documentSelectedIndex = -1;
        state.documentError = null;
        state.documentLoading = true;
        renderElasticsearchDocuments(state);
        var sequence = state.documentSequence;
        var controller = new AbortController();
        state.documentController = controller;
        request('/elasticsearch/datasources/' + encodeURIComponent(datasourceKey) + '/documents?'
            + queryString({index: index, dsl: encodeBase64Url(dsl), page: page, size: 20}), controller.signal)
            .then(function (data) {
                if (state.documentSequence !== sequence || state.selectedDatasourceKey !== datasourceKey) {
                    return;
                }
                state.documentData = data;
                state.documentSelectedIndex = data.items && data.items.length ? 0 : -1;
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

    function effectiveAuditRange(data) {
        if (!data.from || !data.to) {
            return '未返回有效时间范围。';
        }
        return '实际查询范围（UTC）：' + data.from + ' 至 ' + data.to;
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
            KAFKA_TOPIC_RUNTIME: '读取 Topic 运行态',
            KAFKA_CONSUMER_GROUP_LIST: '读取消费组清单',
            KAFKA_CONSUMER_GROUP_LAG_LIST: '读取消费组积压',
            MYSQL_DATASOURCE_CATALOG: '读取数据源目录',
            MYSQL_DATASOURCE_STATUS: '探测数据源状态',
            MYSQL_SELECT: '执行受控查询'
        };
        return names[capability] || '受限读取操作';
    }

    function renderAudit(workspace, data) {
        var state = workspaceStates[workspace];
        var stateNode = document.getElementById(workspace + '-audit-state');
        var body = document.getElementById(workspace + '-audit-body');
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
        clearAuditBody(workspace);
        document.getElementById(workspace + '-audit-page').textContent = '第 ' + state.auditPage + ' 页';
        controls.previous.disabled = state.auditPage <= 1;
        controls.next.disabled = !state.auditHasMore;
        if (!items.length) {
            stateNode.textContent = state.auditPage === 1 ? '暂无可读取审计记录。' : '当前页没有审计记录。';
            return;
        }
        stateNode.textContent = '已展示 ' + items.length + ' 条脱敏审计记录。';
        items.forEach(function (item) {
            var row = document.createElement('tr');
            var values = [item.occurredAt, item.subject, auditOperation(item.capability), item.datasourceKey,
                tagText(item.clusterTag), item.resourceDigest, item.httpStatus, auditContext(workspace, item),
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

    function utcDateTime(value) {
        var match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value);
        if (!match) {
            return null;
        }
        var second = match[6] || '00';
        var timestamp = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]), Number(match[4]),
            Number(match[5]), Number(second));
        var date = new Date(timestamp);
        if (date.getUTCFullYear() !== Number(match[1]) || date.getUTCMonth() !== Number(match[2]) - 1
            || date.getUTCDate() !== Number(match[3]) || date.getUTCHours() !== Number(match[4])
            || date.getUTCMinutes() !== Number(match[5]) || date.getUTCSeconds() !== Number(second)) {
            return null;
        }
        return match[1] + '-' + match[2] + '-' + match[3] + 'T' + match[4] + ':' + match[5] + ':' + second;
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
            if (!from || !to || from >= to || Date.parse(to) - Date.parse(from)
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
        controls.effective.textContent = '正在确认新的 UTC 查询范围。';
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
                previousState.documentData = null;
                previousState.documentDatasourceKey = null;
                previousState.documentSelectedIndex = -1;
                previousState.documentLoading = false;
                renderElasticsearchDocuments(previousState, '切换工作区后已清除查询结果。');
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
                    document.querySelector('#kafka-topic-console-form input[name="topic"]').value = '';
                    document.querySelector('#kafka-lag-console-form input[name="groupId"]').value = '';
                    resetKafkaTopicConsoleState('切换工作区后已清除查询状态。');
                }
            }
        });
        if (workspace !== 'elasticsearch') {
            var state = workspaceStates.elasticsearch;
            cancelDocumentRequest(state);
            state.documentData = null;
            state.documentDatasourceKey = null;
            state.documentSelectedIndex = -1;
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
            + queryString({size: form.get('size')})).then(function (data) {
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
            + queryString({size: form.get('size')})).then(function (data) {
            setPagedResult('kafka-group-list-console-result', data);
        }).catch(function (error) {
            setResult('kafka-group-list-console-result', error.message);
        });
    });

    document.getElementById('elasticsearch-format-dsl').addEventListener('click', function () {
        var input = document.querySelector('#elasticsearch-console-form textarea[name="dsl"]');
        try {
            input.value = JSON.stringify(JSON.parse(input.value), null, 2);
        } catch (error) {
            document.getElementById('elasticsearch-console-result-state').textContent = 'JSON 格式无效。';
        }
    });

    document.getElementById('elasticsearch-console-form').addEventListener('submit', function (event) {
        event.preventDefault();
        var form = new FormData(event.currentTarget);
        var datasourceKey;
        try {
            datasourceKey = requireDatasource('elasticsearch');
        } catch (error) {
            renderElasticsearchDocuments(workspaceStates.elasticsearch, error.message);
            return;
        }
        var dsl = form.get('dsl');
        try {
            JSON.parse(dsl);
        } catch (error) {
            renderElasticsearchDocuments(workspaceStates.elasticsearch, 'JSON 格式无效。');
            return;
        }
        loadElasticsearchDocuments(datasourceKey, form.get('index'), dsl, 1);
    });

    document.getElementById('elasticsearch-console-result-previous').addEventListener('click', function () {
        var state = workspaceStates.elasticsearch;
        if (!state.documentLoading && state.documentData && state.documentData.page > 1) {
            var form = new FormData(document.getElementById('elasticsearch-console-form'));
            loadElasticsearchDocuments(state.documentDatasourceKey, form.get('index'), form.get('dsl'),
                state.documentData.page - 1);
        }
    });

    document.getElementById('elasticsearch-console-result-next').addEventListener('click', function () {
        var state = workspaceStates.elasticsearch;
        if (!state.documentLoading && state.documentData && state.documentData.hasMore) {
            var form = new FormData(document.getElementById('elasticsearch-console-form'));
            loadElasticsearchDocuments(state.documentDatasourceKey, form.get('index'), form.get('dsl'),
                state.documentData.page + 1);
        }
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

    ['redis-console-result', 'kafka-topic-list-console-result', 'kafka-group-list-console-result',
        'mysql-select-console-result', 'kafka-topic-console-result', 'kafka-lag-console-result']
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

    window.addEventListener('hashchange', parseHash);
    parseHash();
}());

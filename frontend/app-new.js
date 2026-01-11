// AI Roundtable Frontend Application - Updated for new API
class AIRoundtableApp {
    constructor() {
        this.currentSessionId = null;
        this.apiBase = '/airt/api';  // 修正：移除 roundtable
        this.roundtableBase = '/airt/api/roundtable';  // 圆桌讨论 API
        this.configBase = '/airt/api/config';  // 配置 API
        this.godModeVisible = false;
        this.isCreatingSession = false;  // 防止重复提交标志
        this.isProceeding = false;  // 防止重复推进标志

        this.init();
    }

    async init() {
        this.bindEvents();
        await this.loadRoles();
        this.resetUI();  // 重置 UI 状态
        this.showToast('AI Roundtable 已启动', 'success');
    }

    /**
     * 重置 UI 到初始状态
     */
    resetUI() {
        this.currentSessionId = null;
        this.isCreatingSession = false;
        document.getElementById('setupPanel').style.display = 'block';
        document.getElementById('discussionPanel').style.display = 'none';
        document.getElementById('discussionHistory').innerHTML = '';
    }

    async loadRoles() {
        try {
            // 使用配置 API
            const url = `${this.configBase}/roles`;
            console.log('Loading roles from:', url);

            const response = await fetch(url);
            console.log('Response status:', response.status);

            if (response.ok) {
                const roles = await response.json();
                console.log('Roles loaded:', roles);
                this.renderRoleCheckboxes(roles);
            } else {
                const errorText = await response.text();
                console.error('Failed to load roles:', response.status, errorText);
                this.showToast('加载角色配置失败: ' + response.statusText, 'error');
            }
        } catch (error) {
            console.error('Failed to load roles:', error);
            this.showToast('网络错误: 无法加载角色配置', 'error');
        }
    }

    renderRoleCheckboxes(roles) {
        const container = document.getElementById('roleSelector');
        if (!container) {
            console.error('roleSelector container not found!');
            return;
        }

        if (!roles || roles.length === 0) {
            container.innerHTML = '<div class="no-roles">没有可用的角色</div>';
            return;
        }

        container.innerHTML = roles.map(role => `
            <label class="role-checkbox ${role.systemRole ? 'selected' : ''}">
                <input type="checkbox" name="roles" value="${role.roleId}" ${role.systemRole ? 'checked' : ''}>
                <span class="role-icon">${role.icon || '🤖'}</span>
                <span class="role-name">${role.displayName || role.roleId}</span>
                <small>${role.description || ''}</small>
            </label>
        `).join('');

        console.log('Rendered', roles.length, 'roles');

        // Re-bind checkbox events
        document.querySelectorAll('.role-checkbox input').forEach(checkbox => {
            checkbox.addEventListener('change', (e) => this.updateRoleSelection(e));
        });
    }

    bindEvents() {
        // Session setup
        document.getElementById('newSessionBtn')?.addEventListener('click', () => this.showSetupPanel());
        document.getElementById('sessionForm')?.addEventListener('submit', (e) => this.createSession(e));
        document.getElementById('cancelSetupBtn')?.addEventListener('click', () => this.hideSetupPanel());

        // Discussion controls
        document.getElementById('nextRoundBtn')?.addEventListener('click', () => this.nextRound());
        document.getElementById('pauseBtn')?.addEventListener('click', () => this.pauseDiscussion());
        document.getElementById('resumeBtn')?.addEventListener('click', () => this.resumeDiscussion());
        document.getElementById('endDiscussionBtn')?.addEventListener('click', () => this.endDiscussion());
        document.getElementById('toggleLayoutBtn')?.addEventListener('click', () => this.toggleLayout());

        // Toggle buttons for collapsible sections
        document.querySelectorAll('.toggle-btn').forEach(btn => {
            btn.addEventListener('click', (e) => this.toggleSection(e));
        });

        // God Mode controls
        document.getElementById('toggleGodModeBtn')?.addEventListener('click', () => this.toggleGodMode());
        document.getElementById('askQuestionBtn')?.addEventListener('click', () => this.askDirectedQuestion());
        document.getElementById('sendWhisperBtn')?.addEventListener('click', () => this.sendWhisper());
        document.getElementById('forceVerifyBtn')?.addEventListener('click', () => this.forceVerification());
        document.getElementById('changeFocusBtn')?.addEventListener('click', () => this.changeFocus());

        // Human input
        document.getElementById('sendHumanInputBtn')?.addEventListener('click', () => this.sendHumanInput());
        const humanInputTextarea = document.getElementById('humanInput');
        if (humanInputTextarea) {
            // Ctrl+Enter to send
            humanInputTextarea.addEventListener('keydown', (e) => {
                if (e.ctrlKey && e.key === 'Enter') {
                    e.preventDefault();
                    this.sendHumanInput();
                }
            });
        }

        // Modal controls
        document.querySelector('.modal-close')?.addEventListener('click', () => this.closeModal());
        document.getElementById('closeModalBtn')?.addEventListener('click', () => this.closeModal());
        document.getElementById('downloadReportBtn')?.addEventListener('click', () => this.downloadReport());
    }

    showSetupPanel() {
        document.getElementById('setupPanel').style.display = 'block';
        document.getElementById('discussionPanel').style.display = 'none';
    }

    hideSetupPanel() {
        document.getElementById('setupPanel').style.display = 'none';
    }

    async createSession(event) {
        event.preventDefault();

        // 防止重复提交
        if (this.isCreatingSession) {
            this.showToast('正在创建会话，请稍候...', 'warning');
            return;
        }

        const formData = new FormData(event.target);
        const selectedRoles = Array.from(document.querySelectorAll('input[name="roles"]:checked'))
                                   .map(cb => cb.value);

        if (selectedRoles.length === 0) {
            this.showToast('请至少选择一个角色', 'error');
            return;
        }

        const request = {
            topic: formData.get('topic'),
            description: formData.get('background'),
            roles: selectedRoles
        };

        this.isCreatingSession = true;
        // 不显示 loading，让用户看到流式输出效果
        // this.showLoading('正在创建讨论...');

        try {
            const response = await fetch(`${this.roundtableBase}/session`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(request)
            });

            if (!response.ok) {
                throw new Error(`Failed to create session: ${response.statusText}`);
            }

            const result = await response.json();

            // 保存新会话 ID
            this.currentSessionId = result.sessionId;
            console.log('Session created:', this.currentSessionId);

            // 隐藏设置面板
            this.hideSetupPanel();

            // 切换到讨论面板
            document.getElementById('setupPanel').style.display = 'none';
            document.getElementById('discussionPanel').style.display = 'block';

            // 启动会话（不需要等待）
            await this.startDiscussion();

            // 开始第一轮讨论（流式输出）
            await this.nextRound();

        } catch (error) {
            console.error('Error creating session:', error);
            this.showToast('创建会话失败: ' + error.message, 'error');
            this.currentSessionId = null; // 清除无效的会话 ID
        } finally {
            this.isCreatingSession = false;
            // 不需要 hideLoading，因为根本没有显示 loading
            // this.hideLoading();
        }
    }

    async startDiscussion() {
        if (!this.currentSessionId) {
            this.showToast('无效的会话 ID', 'error');
            return;
        }

        try {
            const response = await fetch(`${this.roundtableBase}/session/${this.currentSessionId}/start`, {
                method: 'POST'
            });

            if (!response.ok) {
                const errorText = await response.text();
                console.error('Start session failed:', errorText);
                throw new Error(`Failed to start: ${response.statusText} - ${errorText}`);
            }

            // 切换到讨论面板
            document.getElementById('setupPanel').style.display = 'none';
            document.getElementById('discussionPanel').style.display = 'block';
            this.showToast('讨论已开始！', 'success');

            // 加载会话信息
            await this.loadSessionInfo();

        } catch (error) {
            console.error('Error starting discussion:', error);
            this.showToast('启动讨论失败: ' + error.message, 'error');
            // 启动失败时重置 UI
            this.resetUI();
            throw error; // 重新抛出错误，让调用者知道启动失败
        }
    }

    async loadSessionInfo() {
        try {
            const response = await fetch(`${this.roundtableBase}/session/${this.currentSessionId}`);
            if (response.ok) {
                const session = await response.json();
                document.getElementById('sessionTopic').textContent = session.topic;
                this.updateBlackboard(session.blackboard);
            }
        } catch (error) {
            console.error('Error loading session:', error);
        }
    }

    async loadHistory() {
        if (!this.currentSessionId) {
            console.warn('No session ID, cannot load history');
            return;
        }

        try {
            const response = await fetch(`${this.roundtableBase}/session/${this.currentSessionId}/history`);
            console.log('Loading history from:', `${this.roundtableBase}/session/${this.currentSessionId}/history`);

            if (!response.ok) {
                const errorText = await response.text();
                console.error('Failed to load history:', response.status, errorText);
                throw new Error(`Failed to load history: ${response.statusText}`);
            }

            const history = await response.json();
            console.log('History loaded:', history);

            // 确保 history 是数组
            if (!Array.isArray(history)) {
                console.error('History is not an array:', history);
                this.renderHistory([]);
            } else {
                this.renderHistory(history);
            }

        } catch (error) {
            console.error('Error loading history:', error);
            this.showToast('加载讨论历史失败: ' + error.message, 'error');
            this.renderHistory([]); // 显示空状态
        }
    }

    renderHistory(history) {
        const container = document.getElementById('discussionHistory');
        if (!container) {
            console.error('Discussion history container not found!');
            return;
        }

        // 清空容器
        container.innerHTML = '';

        if (!history || history.length === 0) {
            container.innerHTML = '<div class="no-history">暂无讨论记录</div>';
            return;
        }

        console.log('Rendering history:', history.length, 'messages');

        // 渲染每条消息
        history.forEach((msg, index) => {
            const roundElement = document.createElement('div');
            roundElement.className = 'discussion-round';

            // 格式化时间戳
            const timestamp = msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString() : '';

            // 构建 HTML
            roundElement.innerHTML = `
                <div class="round-header">
                    <div class="speaker-info">
                        <span class="round-badge">第 ${index + 1} 轮</span>
                        <span class="speaker-role">${msg.roleDisplayName || '未知角色'}</span>
                        <span class="round-time">${timestamp}</span>
                    </div>
                </div>

                ${msg.thinkingProcess ? `
                    <details class="thinking-details">
                        <summary class="thinking-summary">💭 查看思考过程</summary>
                        <div class="thinking-content">${this.formatContent(msg.thinkingProcess)}</div>
                    </details>
                ` : ''}

                <div class="round-statement">${this.formatContent(msg.publicResponse || '无内容')}</div>

                ${msg.keyInsights && msg.keyInsights.length > 0 ? `
                    <div class="key-insights">
                        <strong>🔍 关键洞察：</strong>
                        <ul>
                            ${msg.keyInsights.map(insight => `<li>${insight}</li>`).join('')}
                        </ul>
                    </div>
                ` : ''}

                ${msg.toolCalls && msg.toolCalls.length > 0 ? `
                    <div class="tool-calls">
                        <strong>🔧 工具调用：</strong>
                        ${msg.toolCalls.map(tool => `
                            <div class="tool-call">
                                <span class="tool-name">${tool.toolName}</span>
                                <span class="tool-status ${tool.success ? 'success' : 'failed'}">
                                    ${tool.success ? '✅' : '❌'}
                                </span>
                            </div>
                        `).join('')}
                    </div>
                ` : ''}
            `;

            container.appendChild(roundElement);
        });

        // 滚动到底部
        container.scrollTop = container.scrollHeight;
    }

    formatContent(content) {
        if (!content) return '';
        // 转义 HTML 以防止 XSS
        let formatted = content
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
        // 转换换行为 <br>
        formatted = formatted.replace(/\n/g, '<br>');
        // 转换 **加粗** 为 <strong>
        formatted = formatted.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
        return formatted;
    }

    updateBlackboard(blackboard) {
        if (!blackboard) {
            console.warn('Blackboard data is empty');
            return;
        }

        console.log('Updating blackboard:', blackboard);

        // Update discussion summary
        const summaryContainer = document.getElementById('discussionSummary');
        if (summaryContainer) {
            summaryContainer.innerHTML = '';

            if (!blackboard.discussionSummary || blackboard.discussionSummary.trim() === '') {
                summaryContainer.innerHTML = '<div class="empty-message">暂无摘要</div>';
            } else {
                const summaryElement = document.createElement('div');
                summaryElement.className = 'summary-text';
                summaryElement.textContent = blackboard.discussionSummary;
                summaryContainer.appendChild(summaryElement);
            }
        }

        // Update key insights
        const insightsContainer = document.getElementById('keyInsights');
        if (insightsContainer) {
            insightsContainer.innerHTML = '';

            if (!blackboard.keyInsights || blackboard.keyInsights.length === 0) {
                insightsContainer.innerHTML = '<div class="empty-message">暂无关键洞察</div>';
            } else {
                blackboard.keyInsights.forEach(insight => {
                    const insightElement = document.createElement('div');
                    insightElement.className = 'insight-item';
                    insightElement.innerHTML = `
                        <strong>${insight.proponent || '未知'}:</strong>
                        ${this.formatContent(insight.text || '')}
                        ${insight.status ? `<small class="insight-status">(${this.translateInsightStatus(insight.status)})</small>` : ''}
                    `;
                    insightsContainer.appendChild(insightElement);
                });
            }
        }

        // Update consensus points
        const consensusContainer = document.getElementById('consensusPoints');
        if (consensusContainer) {
            consensusContainer.innerHTML = '';

            if (!blackboard.consensusPoints || blackboard.consensusPoints.length === 0) {
                consensusContainer.innerHTML = '<div class="empty-message">暂无共识点</div>';
            } else {
                blackboard.consensusPoints.forEach(point => {
                    const pointElement = document.createElement('div');
                    pointElement.className = 'consensus-item';
                    pointElement.textContent = point;
                    consensusContainer.appendChild(pointElement);
                });
            }
        }

        // Update conflict points
        const conflictContainer = document.getElementById('conflictMap');
        if (conflictContainer) {
            conflictContainer.innerHTML = '';

            if (!blackboard.conflictPoints || blackboard.conflictPoints.length === 0) {
                conflictContainer.innerHTML = '<div class="empty-message">暂无分歧点</div>';
            } else {
                blackboard.conflictPoints.forEach(conflict => {
                    const conflictElement = document.createElement('div');
                    conflictElement.className = 'conflict-item';

                    // 处理不同格式的冲突点数据
                    if (typeof conflict === 'string') {
                        conflictElement.textContent = conflict;
                    } else if (conflict.topic) {
                        conflictElement.innerHTML = `
                            <strong>📌 ${this.formatContent(conflict.topic)}</strong>
                            ${conflict.reason ? `<br><small>${this.formatContent(conflict.reason)}</small>` : ''}
                        `;
                    } else {
                        conflictElement.textContent = JSON.stringify(conflict);
                    }

                    conflictContainer.appendChild(conflictElement);
                });
            }
        }

        // Update pending questions
        const questionsContainer = document.getElementById('pendingQuestions');
        if (questionsContainer) {
            questionsContainer.innerHTML = '';

            if (!blackboard.pendingQuestions || blackboard.pendingQuestions.length === 0) {
                questionsContainer.innerHTML = '<div class="empty-message">暂无待解决问题</div>';
            } else {
                blackboard.pendingQuestions.forEach(question => {
                    const questionElement = document.createElement('div');
                    questionElement.className = 'question-item';
                    questionElement.textContent = question;
                    questionsContainer.appendChild(questionElement);
                });
            }
        }
    }

    /**
     * 翻译洞察状态
     */
    translateInsightStatus(status) {
        const statusMap = {
            'PROPOSED': '已提出',
            'CONTESTED': '争议中',
            'ACCEPTED': '已共识',
            'REJECTED': '已拒绝',
            'PENDING_VERIFICATION': '待验证'
        };
        return statusMap[status] || status;
    }

    async nextRound() {
        if (!this.currentSessionId) {
            this.showToast('没有活动会话', 'warning');
            return;
        }

        // 防止重复提交
        if (this.isProceeding) {
            this.showToast('正在推进讨论，请稍候...', 'warning');
            return;
        }

        // 获取选择的轮数
        const roundCountSelect = document.getElementById('roundCount');
        const roundsToProceed = roundCountSelect ? parseInt(roundCountSelect.value) : 1;

        this.isProceeding = true;
        const nextRoundBtn = document.getElementById('nextRoundBtn');
        const originalText = nextRoundBtn.textContent;

        // 使用按钮 loading 状态
        nextRoundBtn.disabled = true;
        nextRoundBtn.textContent = `推进 ${roundsToProceed} 轮中...`;
        nextRoundBtn.classList.add('btn-loading');

        try {
            // 循环推进多轮
            for (let i = 0; i < roundsToProceed; i++) {
                await this.proceedRoundStream();
            }

        } catch (error) {
            console.error('Error proceeding to next round:', error);
            this.showToast('推进讨论失败: ' + error.message, 'error');
        } finally {
            this.isProceeding = false;
            nextRoundBtn.disabled = false;
            nextRoundBtn.textContent = originalText;
            nextRoundBtn.classList.remove('btn-loading');
        }
    }

    /**
     * 使用 SSE 流式接口推进一轮
     */
    async proceedRoundStream() {
        return new Promise((resolve, reject) => {
            const eventSource = new EventSource(
                `${this.roundtableBase}/session/${this.currentSessionId}/proceed-stream`
            );

            let currentAgentInfo = null;
            let currentContent = '';
            let currentRoundElement = null;

            eventSource.onmessage = (event) => {
                try {
                    // 添加调试日志
                    console.log('SSE received:', event.data);

                    // 检查数据格式
                    let rawData = event.data.trim();

                    // 跳过空消息或注释
                    if (!rawData || rawData.startsWith(':')) {
                        return;
                    }

                    // 解析 JSON
                    const data = JSON.parse(rawData);
                    console.log('Parsed SSE data:', data);

                    switch (data.type) {
                        case 'start':
                            // 开始生成
                            currentAgentInfo = {
                                agentId: data.agentId,
                                roleName: data.roleName,
                                roleDisplayName: data.roleDisplayName,
                                round: data.round
                            };
                            currentContent = '';

                            // 创建新的轮次元素
                            currentRoundElement = this.createStreamingRoundElement(currentAgentInfo);
                            this.appendRoundToHistory(currentRoundElement);
                            break;

                        case 'token':
                            // 接收到 token，实时更新
                            currentContent += data.content;
                            this.updateStreamingContent(currentRoundElement, currentContent);
                            break;

                        case 'complete':
                            // 完成，保存到历史
                            const response = data.response;
                            this.finalizeStreamingRound(currentRoundElement, response);

                            // 更新白板
                            if (response.blackboardUpdate) {
                                // 需要从后端获取最新的白板数据
                                this.loadSessionInfo();
                            }

                            eventSource.close();
                            resolve();
                            break;

                        case 'error':
                            // 错误
                            console.error('Stream error:', data.error);
                            this.showToast('生成出错: ' + data.error, 'error');
                            eventSource.close();
                            reject(new Error(data.error));
                            break;

                        default:
                            console.warn('Unknown SSE message type:', data.type);
                    }

                } catch (error) {
                    console.error('Error parsing SSE data:', error);
                    console.error('Raw data:', event.data);
                    this.showToast('解析数据失败: ' + error.message, 'error');
                    eventSource.close();
                    reject(error);
                }
            };

            eventSource.onerror = (error) => {
                console.error('EventSource error:', error);
                eventSource.close();
                this.showToast('连接中断', 'error');
                reject(error);
            };
        });
    }

    /**
     * 创建流式轮次元素
     */
    createStreamingRoundElement(agentInfo) {
        const roundDiv = document.createElement('div');
        roundDiv.className = 'discussion-round streaming-round';
        const roundText = agentInfo.round ? `第 ${agentInfo.round} 轮` : '生成中...';
        roundDiv.innerHTML = `
            <div class="round-header">
                <span class="role-badge">${agentInfo.roleDisplayName}</span>
                <span class="round-number">${roundText}</span>
            </div>
            <div class="round-content">
                <div class="streaming-content"></div>
            </div>
        `;
        return roundDiv;
    }

    /**
     * 添加轮次到历史记录
     */
    appendRoundToHistory(roundElement) {
        const historyContainer = document.getElementById('discussionHistory');
        if (historyContainer) {
            historyContainer.appendChild(roundElement);
            // 滚动到底部
            historyContainer.scrollTop = historyContainer.scrollHeight;
        }
    }

    /**
     * 更新流式内容
     */
    updateStreamingContent(roundElement, content) {
        const contentDiv = roundElement.querySelector('.streaming-content');
        if (contentDiv) {
            // 格式化内容
            contentDiv.innerHTML = this.formatContent(content);
            // 滚动到底部
            const historyContainer = document.getElementById('discussionHistory');
            if (historyContainer) {
                historyContainer.scrollTop = historyContainer.scrollHeight;
            }
        }
    }

    /**
     * 完成流式轮次
     */
    finalizeStreamingRound(roundElement, response) {
        // 移除 streaming 类
        roundElement.classList.remove('streaming-round');

        // 更新轮次号
        const roundNumberSpan = roundElement.querySelector('.round-number');
        if (roundNumberSpan) {
            const roundText = response.round ? `第 ${response.round} 轮` : '第 1 轮';
            roundNumberSpan.textContent = roundText;
        }

        // 替换内容为最终格式
        const contentDiv = roundElement.querySelector('.round-content');
        if (contentDiv) {
            contentDiv.innerHTML = `
                <div class="message-content">
                    ${this.formatContent(response.publicResponse)}
                </div>
                ${response.thinkingProcess ? `
                    <details class="thinking-details">
                        <summary class="thinking-summary">💭 查看思考过程</summary>
                        <div class="thinking-content">
                            ${this.formatContent(response.thinkingProcess)}
                        </div>
                    </details>
                ` : ''}
            `;
        }

        // 只刷新白板，不重新加载历史记录（避免清空流式输出内容）
        this.loadSessionInfo();
    }

    async pauseDiscussion() {
        if (!this.currentSessionId) return;

        try {
            await fetch(`${this.roundtableBase}/session/${this.currentSessionId}/pause`, {
                method: 'POST'
            });

            document.getElementById('pauseBtn').style.display = 'none';
            document.getElementById('resumeBtn').style.display = 'inline-block';
            this.showToast('讨论已暂停', 'warning');

        } catch (error) {
            console.error('Error pausing discussion:', error);
            this.showToast('操作失败: ' + error.message, 'error');
        }
    }

    async resumeDiscussion() {
        if (!this.currentSessionId) return;

        try {
            await fetch(`${this.roundtableBase}/session/${this.currentSessionId}/resume`, {
                method: 'POST'
            });

            document.getElementById('pauseBtn').style.display = 'inline-block';
            document.getElementById('resumeBtn').style.display = 'none';
            this.showToast('讨论已恢复', 'success');

        } catch (error) {
            console.error('Error resuming discussion:', error);
            this.showToast('操作失败: ' + error.message, 'error');
        }
    }

    async endDiscussion() {
        if (!this.currentSessionId) return;

        if (!confirm('确定要结束讨论吗？')) {
            return;
        }

        this.showLoading('正在结束讨论...');

        try {
            await fetch(`${this.roundtableBase}/session/${this.currentSessionId}/finish`, {
                method: 'POST'
            });

            await this.loadSessionInfo();
            this.showToast('讨论已结束', 'success');

        } catch (error) {
            console.error('Error ending discussion:', error);
            this.showToast('网络错误: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    // God Mode functions
    toggleGodMode() {
        this.godModeVisible = !this.godModeVisible;
        const console = document.getElementById('godModeConsole');
        const button = document.getElementById('toggleGodModeBtn');

        if (this.godModeVisible) {
            console.style.display = 'block';
            button.textContent = '隐藏控制台';
        } else {
            console.style.display = 'none';
            button.textContent = '显示控制台';
        }
    }

    async askDirectedQuestion() {
        const targetRoleId = document.getElementById('targetAgentSelect')?.value;
        const question = document.getElementById('directedQuestion')?.value.trim();

        if (!targetRoleId || !question) {
            this.showToast('请选择目标角色并输入问题', 'warning');
            return;
        }

        try {
            await fetch(`${this.roundtableBase}/session/${this.currentSessionId}/question`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ roleId: targetRoleId, question })
            });

            document.getElementById('directedQuestion').value = '';
            this.showToast('问题已发送', 'success');
        } catch (error) {
            this.showToast('发送失败', 'error');
        }
    }

    async sendWhisper() {
        const message = document.getElementById('whisperMessage')?.value.trim();
        if (!message) {
            this.showToast('请输入私信内容', 'warning');
            return;
        }
        this.showToast('私信功能即将推出', 'info');
    }

    async forceVerification() {
        const statement = document.getElementById('verifyStatement')?.value.trim();
        if (!statement) {
            this.showToast('请输入要验证的内容', 'warning');
            return;
        }
        this.showToast('验证功能即将推出', 'info');
    }

    async changeFocus() {
        const newFocus = document.getElementById('newFocus')?.value.trim();
        if (!newFocus) {
            this.showToast('请输入新的讨论焦点', 'warning');
            return;
        }
        this.showToast('改变焦点功能即将推出', 'info');
    }

    showDecisionPackage(data) {
        const modal = document.getElementById('decisionModal');
        const content = document.getElementById('decisionPackageContent');

        content.innerHTML = `
            <h3>${data.topic || '讨论报告'}</h3>
            <p>轮次: ${data.rounds || 0}</p>
            <p>状态: ${data.summary || '已完成'}</p>
        `;

        modal.style.display = 'flex';
    }

    closeModal() {
        document.getElementById('decisionModal').style.display = 'none';
    }

    downloadReport() {
        this.showToast('报告下载功能即将推出', 'info');
    }

    // Utility functions
    getRoleDisplayName(role) {
        const roleNames = {
            'product_manager': '产品经理',
            'backend_engineer': '后端工程师',
            'frontend_engineer': '前端工程师',
            'business_owner': '业务专家',
            'devil_advocate': '挑战者',
            'moderator': '主持人',
            'domain_expert': '技术专家',
            'scribe': '记录员'
        };
        return roleNames[role] || role;
    }

    showLoading(message = '加载中...') {
        const overlay = document.getElementById('loadingOverlay');
        if (overlay) {
            const p = overlay.querySelector('p');
            if (p) p.textContent = message;
            overlay.style.display = 'flex';
        }
    }

    hideLoading() {
        const overlay = document.getElementById('loadingOverlay');
        if (overlay) overlay.style.display = 'none';
    }

    showToast(message, type = 'info') {
        const container = document.getElementById('toastContainer');
        if (!container) return;

        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = message;

        container.appendChild(toast);

        setTimeout(() => toast.remove(), 5000);
    }

    updateRoleSelection(event) {
        const checkbox = event.target;
        const label = checkbox.closest('.role-checkbox');

        if (checkbox.checked) {
            label.classList.add('selected');
        } else {
            label.classList.remove('selected');
        }
    }

    /**
     * 切换布局（折叠/展开）
     */
    toggleLayout() {
        const layout = document.getElementById('discussionLayout');
        const btn = document.getElementById('toggleLayoutBtn');

        if (layout.classList.contains('collapsed')) {
            layout.classList.remove('collapsed');
            btn.textContent = '📁 折叠/展开';
        } else {
            layout.classList.add('collapsed');
            btn.textContent = '📂 展开';
        }
    }

    /**
     * 切换指定区域的折叠状态
     */
    toggleSection(event) {
        const btn = event.target;
        const targetId = btn.dataset.target;
        const targetElement = document.getElementById(targetId);

        if (!targetElement) return;

        if (targetElement.classList.contains('collapsed')) {
            targetElement.classList.remove('collapsed');
            btn.textContent = '−';
        } else {
            targetElement.classList.add('collapsed');
            btn.textContent = '+';
        }
    }

    /**
     * 发送人类输入到讨论中
     */
    async sendHumanInput() {
        const textarea = document.getElementById('humanInput');
        const sendBtn = document.getElementById('sendHumanInputBtn');
        const inputText = textarea?.value?.trim();

        if (!inputText) {
            this.showToast('请输入内容', 'warning');
            return;
        }

        if (!this.currentSessionId) {
            this.showToast('没有活动会话', 'warning');
            return;
        }

        // 禁用输入
        textarea.disabled = true;
        sendBtn.disabled = true;
        const originalBtnText = sendBtn.innerHTML;
        sendBtn.innerHTML = '<span class="btn-icon">⏳</span><span class="btn-text">发送中...</span>';

        try {
            const response = await fetch(`${this.roundtableBase}/session/${this.currentSessionId}/human-input`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    content: inputText,
                    timestamp: Date.now()
                })
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`发送失败: ${response.statusText} - ${errorText}`);
            }

            // 清空输入框
            textarea.value = '';

            // 刷新历史记录
            await this.loadHistory();
            await this.loadSessionInfo();

            this.showToast('发言已发送', 'success');

        } catch (error) {
            console.error('Failed to send human input:', error);
            this.showToast(error.message, 'error');
        } finally {
            // 恢复输入
            textarea.disabled = false;
            sendBtn.disabled = false;
            sendBtn.innerHTML = originalBtnText;
            textarea.focus();
        }
    }
}

// Initialize app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new AIRoundtableApp();
});

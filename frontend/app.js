// AI Roundtable Frontend Application
class AIRoundtableApp {
    constructor() {
        this.currentSession = null;
        this.apiBase = '/airt/api/roundtable';
        this.godModeVisible = false;

        this.init();
    }

    async init() {
        this.bindEvents();
        await this.loadRoles();
        this.showToast('AI Roundtable 已启动', 'success');
    }

    async loadRoles() {
        try {
            const response = await fetch(`${this.apiBase}/roles`);
            if (response.ok) {
                const roles = await response.json();
                this.renderRoleCheckboxes(roles);
            }
        } catch (error) {
            console.error('Failed to load roles:', error);
        }
    }

    renderRoleCheckboxes(roles) {
        const container = document.querySelector('.role-selector');
        if (!container) return;

        container.innerHTML = roles.map(role => `
            <label class="role-checkbox">
                <input type="checkbox" name="roles" value="${role.roleId}" ${role.systemRole ? 'checked' : ''}>
                <span class="role-icon">${role.icon || '🤖'}</span>
                <span class="role-name">${role.displayName}</span>
                <small>${role.description || ''}</small>
            </label>
        `).join('');

        // Re-bind checkbox events
        document.querySelectorAll('.role-checkbox input').forEach(checkbox => {
            checkbox.addEventListener('change', (e) => this.updateRoleSelection(e));
        });
    }

    bindEvents() {
        // Session setup
        document.getElementById('newSessionBtn').addEventListener('click', () => this.showSetupPanel());
        document.getElementById('sessionForm').addEventListener('submit', (e) => this.createSession(e));
        document.getElementById('cancelSetupBtn').addEventListener('click', () => this.hideSetupPanel());

        // Discussion controls
        document.getElementById('nextRoundBtn').addEventListener('click', () => this.nextRound());
        document.getElementById('pauseBtn').addEventListener('click', () => this.pauseDiscussion());
        document.getElementById('resumeBtn').addEventListener('click', () => this.resumeDiscussion());
        document.getElementById('endDiscussionBtn').addEventListener('click', () => this.endDiscussion());
        
        // God Mode controls
        document.getElementById('toggleGodModeBtn').addEventListener('click', () => this.toggleGodMode());
        document.getElementById('askQuestionBtn').addEventListener('click', () => this.askDirectedQuestion());
        document.getElementById('sendWhisperBtn').addEventListener('click', () => this.sendWhisper());
        document.getElementById('forceVerifyBtn').addEventListener('click', () => this.forceVerification());
        document.getElementById('changeFocusBtn').addEventListener('click', () => this.changeFocus());
        
        // Modal controls
        document.querySelector('.modal-close').addEventListener('click', () => this.closeModal());
        document.getElementById('closeModalBtn').addEventListener('click', () => this.closeModal());
        document.getElementById('downloadReportBtn').addEventListener('click', () => this.downloadReport());
        
        // Role checkboxes
        document.querySelectorAll('.role-checkbox input').forEach(checkbox => {
            checkbox.addEventListener('change', (e) => this.updateRoleSelection(e));
        });
    }
    
    initWebSocket() {
        const socket = new SockJS('/airt/airt-websocket');
        this.stompClient = Stomp.over(socket);
        
        this.stompClient.connect({}, 
            (frame) => {
                this.isConnected = true;
                console.log('WebSocket connected:', frame);
                this.showToast('实时连接已建立', 'success');
            },
            (error) => {
                this.isConnected = false;
                console.error('WebSocket connection error:', error);
                this.showToast('连接断开，尝试重新连接...', 'warning');
                // Retry connection after 5 seconds
                setTimeout(() => this.initWebSocket(), 5000);
            }
        );
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
        
        const formData = new FormData(event.target);
        const selectedRoles = Array.from(document.querySelectorAll('input[name="roles"]:checked'))
                                   .map(cb => cb.value);
        
        if (selectedRoles.length === 0) {
            this.showToast('请至少选择一个角色', 'error');
            return;
        }
        
        const request = {
            topic: formData.get('topic'),
            backgroundInfo: formData.get('background'),
            selectedAgentRoles: selectedRoles,
            autoSelectAgents: false
        };
        
        this.showLoading('正在创建讨论...');
        
        try {
            const response = await fetch('/airt/api/roundtable/sessions', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(request)
            });
            
            const result = await response.json();
            
            if (result.success) {
                this.currentSession = result.data;
                this.startDiscussion(result.data);
                this.subscribeToSession(result.data.sessionId);
            } else {
                this.showToast('创建失败: ' + result.error, 'error');
            }
        } catch (error) {
            console.error('Error creating session:', error);
            this.showToast('网络错误: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }
    
    startDiscussion(session) {
        document.getElementById('setupPanel').style.display = 'none';
        document.getElementById('discussionPanel').style.display = 'block';
        document.getElementById('sessionTopic').textContent = session.topic;
        
        // Populate agent selector for God Mode
        this.populateAgentSelector(session.agentIds);
        
        this.showToast('讨论已开始！', 'success');
    }
    
    subscribeToSession(sessionId) {
        if (!this.isConnected) return;
        
        // Subscribe to round updates
        this.stompClient.subscribe(`/topic/session/${sessionId}/rounds`, (message) => {
            const update = JSON.parse(message.body);
            this.handleRoundUpdate(update);
        });
        
        // Subscribe to blackboard updates
        this.stompClient.subscribe(`/topic/session/${sessionId}/blackboard`, (message) => {
            const update = JSON.parse(message.body);
            this.handleBlackboardUpdate(update);
        });
        
        // Subscribe to session status
        this.stompClient.subscribe(`/topic/session/${sessionId}/status`, (message) => {
            const update = JSON.parse(message.body);
            this.handleStatusUpdate(update);
        });
        
        // Subscribe to errors
        this.stompClient.subscribe(`/topic/session/${sessionId}/errors`, (message) => {
            const error = JSON.parse(message.body);
            this.showToast('错误: ' + error.payload, 'error');
        });
    }
    
    handleRoundUpdate(message) {
        const round = message.payload;
        this.addRoundToHistory(round);
        this.showToast(`${round.speakerName} 发表了观点`, 'info');
    }
    
    handleBlackboardUpdate(message) {
        const blackboard = message.payload;
        this.updateBlackboard(blackboard);
    }
    
    handleStatusUpdate(message) {
        const status = message.payload.status;
        this.updateSessionStatus(status);
    }
    
    addRoundToHistory(round) {
        const historyContainer = document.getElementById('discussionHistory');
        
        const roundElement = document.createElement('div');
        roundElement.className = 'discussion-round';
        roundElement.innerHTML = `
            <div class="round-header">
                <div class="speaker-info">
                    <span class="speaker-role">${this.getRoleDisplayName(round.speakerRole)}</span>
                    <span class="speaker-name">${round.speakerName}</span>
                </div>
                <span class="round-number">#${round.roundNumber}</span>
            </div>
            <div class="round-statement">${round.statement}</div>
            ${round.thinkingProcess ? `<div class="thinking-process">${round.thinkingProcess}</div>` : ''}
        `;
        
        historyContainer.appendChild(roundElement);
        historyContainer.scrollTop = historyContainer.scrollHeight;
    }
    
    updateBlackboard(blackboard) {
        // Update key insights
        const insightsContainer = document.getElementById('keyInsights');
        insightsContainer.innerHTML = '';
        blackboard.keyInsights.forEach(insight => {
            const insightElement = document.createElement('div');
            insightElement.className = 'insight-item';
            insightElement.innerHTML = `
                <strong>${insight.proponent}:</strong> ${insight.text}
                <small>(${insight.status})</small>
            `;
            insightsContainer.appendChild(insightElement);
        });
        
        // Update consensus points
        const consensusContainer = document.getElementById('consensusPoints');
        consensusContainer.innerHTML = '';
        blackboard.consensusPoints.forEach(point => {
            const pointElement = document.createElement('div');
            pointElement.className = 'consensus-item';
            pointElement.textContent = point;
            consensusContainer.appendChild(pointElement);
        });
        
        // Update conflicts
        const conflictsContainer = document.getElementById('conflictMap');
        conflictsContainer.innerHTML = '';
        blackboard.conflictMap.forEach(conflict => {
            const conflictElement = document.createElement('div');
            conflictElement.className = 'conflict-item';
            conflictElement.innerHTML = `
                <strong>${conflict.topic}</strong><br>
                ${conflict.opposingViews.join(' vs ')}
            `;
            conflictsContainer.appendChild(conflictElement);
        });
        
        // Update pending questions
        const questionsContainer = document.getElementById('pendingQuestions');
        questionsContainer.innerHTML = '';
        blackboard.pendingQuestions.forEach(question => {
            const questionElement = document.createElement('div');
            questionElement.className = 'question-item';
            questionElement.textContent = question;
            questionsContainer.appendChild(questionElement);
        });
    }
    
    updateSessionStatus(status) {
        const statusDisplay = document.getElementById('sessionStatus');
        if (statusDisplay) {
            statusDisplay.textContent = this.getStatusDisplayName(status);
        }
    }
    
    async nextRound() {
        if (!this.currentSession) return;
        
        this.showLoading('AI 正在思考...');
        
        try {
            const response = await fetch(`/airt/api/roundtable/sessions/${this.currentSession.sessionId}/next`, {
                method: 'POST'
            });
            
            const result = await response.json();
            
            if (!result.success) {
                this.showToast('操作失败: ' + result.error, 'error');
            }
        } catch (error) {
            console.error('Error proceeding to next round:', error);
            this.showToast('网络错误: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }
    
    async pauseDiscussion() {
        if (!this.currentSession) return;
        
        try {
            const response = await fetch(`/airt/api/roundtable/sessions/${this.currentSession.sessionId}/intervene`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    type: 'PAUSE',
                    autoResume: false
                })
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
        if (!this.currentSession) return;
        
        try {
            const response = await fetch(`/airt/api/roundtable/sessions/${this.currentSession.sessionId}/intervene`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    type: 'RESUME',
                    autoResume: true
                })
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
        if (!this.currentSession) return;
        
        if (!confirm('确定要结束讨论吗？这将生成最终决策报告。')) {
            return;
        }
        
        this.showLoading('正在生成决策报告...');
        
        try {
            const response = await fetch(`/airt/api/roundtable/sessions/${this.currentSession.sessionId}/decision`);
            const result = await response.json();
            
            if (result.success) {
                this.showDecisionPackage(result.data);
            } else {
                this.showToast('生成报告失败: ' + result.error, 'error');
            }
        } catch (error) {
            console.error('Error generating decision package:', error);
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
    
    populateAgentSelector(agentIds) {
        const select = document.getElementById('targetAgentSelect');
        select.innerHTML = '<option value="">选择目标角色</option>';
        
        // Mock agent data - in real app, this would come from session data
        const roles = [
            { id: 'moderator', name: '主持人 (Alex Chen)', role: 'MODERATOR' },
            { id: 'expert', name: '领域专家 (Dr. Sarah Kim)', role: 'DOMAIN_EXPERT' },
            { id: 'devil', name: '挑战者 (Jordan Rivers)', role: 'DEVILS_ADVOCATE' },
            { id: 'product', name: '价值官 (Morgan Lee)', role: 'PRODUCT_BIZ' },
            { id: 'scribe', name: '记录员 (Taylor Notes)', role: 'SCRIBE' }
        ];
        
        roles.forEach(agent => {
            const option = document.createElement('option');
            option.value = agent.id;
            option.textContent = agent.name;
            select.appendChild(option);
        });
    }
    
    async askDirectedQuestion() {
        const targetAgentId = document.getElementById('targetAgentSelect').value;
        const question = document.getElementById('directedQuestion').value.trim();
        
        if (!targetAgentId || !question) {
            this.showToast('请选择目标角色并输入问题', 'warning');
            return;
        }
        
        await this.sendIntervention({
            type: 'DIRECTED_QUESTION',
            content: question,
            targetAgentId: targetAgentId,
            autoResume: true
        });
        
        document.getElementById('directedQuestion').value = '';
    }
    
    async sendWhisper() {
        const message = document.getElementById('whisperMessage').value.trim();
        
        if (!message) {
            this.showToast('请输入私信内容', 'warning');
            return;
        }
        
        await this.sendIntervention({
            type: 'WHISPER',
            content: message,
            autoResume: true
        });
        
        document.getElementById('whisperMessage').value = '';
        this.showToast('私信已发送给主持人', 'success');
    }
    
    async forceVerification() {
        const statement = document.getElementById('verifyStatement').value.trim();
        
        if (!statement) {
            this.showToast('请选择或输入要验证的内容', 'warning');
            return;
        }
        
        await this.sendIntervention({
            type: 'FORCE_MCP_VERIFY',
            content: statement,
            autoResume: true
        });
        
        document.getElementById('verifyStatement').value = '';
        this.showToast('已触发强制验证', 'info');
    }
    
    async changeFocus() {
        const newFocus = document.getElementById('newFocus').value.trim();
        
        if (!newFocus) {
            this.showToast('请输入新的讨论焦点', 'warning');
            return;
        }
        
        await this.sendIntervention({
            type: 'CHANGE_FOCUS',
            content: newFocus,
            autoResume: true
        });
        
        document.getElementById('newFocus').value = '';
        this.showToast('讨论焦点已改变', 'success');
    }
    
    async sendIntervention(interventionData) {
        if (!this.currentSession) return;
        
        try {
            const response = await fetch(`/airt/api/roundtable/sessions/${this.currentSession.sessionId}/intervene`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(interventionData)
            });
            
            const result = await response.json();
            
            if (!result.success) {
                this.showToast('干预失败: ' + result.error, 'error');
            }
        } catch (error) {
            console.error('Error sending intervention:', error);
            this.showToast('网络错误: ' + error.message, 'error');
        }
    }
    
    showDecisionPackage(decisionPackage) {
        const modal = document.getElementById('decisionModal');
        const content = document.getElementById('decisionPackageContent');
        
        content.innerHTML = `
            <h3>${decisionPackage.topic}</h3>
            
            <h4>执行摘要</h4>
            <div class="executive-summary">
                <pre>${decisionPackage.executiveSummary}</pre>
            </div>
            
            <h4>已达成共识</h4>
            <ul>
                ${decisionPackage.consensusPoints.map(point => `<li>${point}</li>`).join('')}
            </ul>
            
            <h4>关键洞察</h4>
            <ul>
                ${decisionPackage.keyInsights
                    .filter(insight => insight.status === 'ACCEPTED')
                    .map(insight => `<li><strong>${insight.proponent}:</strong> ${insight.text}</li>`)
                    .join('')}
            </ul>
            
            <h4>待解决分歧</h4>
            <ul>
                ${decisionPackage.conflictMap.map(conflict => 
                    `<li><strong>${conflict.topic}</strong>: ${conflict.opposingViews.join(' vs ')}</li>`
                ).join('')}
            </ul>
            
            <h4>事实记录</h4>
            <ul>
                ${decisionPackage.factLog.map(fact => 
                    `<li><strong>${fact.statement}</strong> - ${fact.verificationResult} (${fact.source})</li>`
                ).join('')}
            </ul>
        `;
        
        modal.style.display = 'flex';
    }
    
    closeModal() {
        document.getElementById('decisionModal').style.display = 'none';
    }
    
    downloadReport() {
        // In a real app, this would generate and download a PDF report
        this.showToast('报告下载功能即将推出', 'info');
    }
    
    // Utility functions
    getRoleDisplayName(role) {
        const roleNames = {
            'MODERATOR': '主持人',
            'DOMAIN_EXPERT': '专家',
            'DEVILS_ADVOCATE': '挑战者',
            'PRODUCT_BIZ': '价值官',
            'SCRIBE': '记录员'
        };
        return roleNames[role] || role;
    }
    
    getStatusDisplayName(status) {
        const statusNames = {
            'INIT': '初始化',
            'OPENING': '开场陈述',
            'DEBATE': '自由辩论',
            'CHECK_FACT': '事实核查',
            'HUMAN_INTERVENTION': '人类干预',
            'SYNTHESIS': '总结阶段',
            'FINISHED': '已完成',
            'TIMEOUT': '已超时'
        };
        return statusNames[status] || status;
    }
    
    showLoading(message = '加载中...') {
        document.getElementById('loadingOverlay').querySelector('p').textContent = message;
        document.getElementById('loadingOverlay').style.display = 'flex';
    }
    
    hideLoading() {
        document.getElementById('loadingOverlay').style.display = 'none';
    }
    
    showToast(message, type = 'info') {
        const container = document.getElementById('toastContainer');
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = message;
        
        container.appendChild(toast);
        
        // Auto remove after 5 seconds
        setTimeout(() => {
            toast.remove();
        }, 5000);
    }
    
    updateRoleSelection(event) {
        const checkbox = event.target;
        const roleIcon = checkbox.nextElementSibling;
        
        if (checkbox.checked) {
            roleIcon.style.backgroundColor = '#4299e1';
            roleIcon.style.color = 'white';
        } else {
            roleIcon.style.backgroundColor = '#edf2f7';
            roleIcon.style.color = 'inherit';
        }
    }
}

// Initialize app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new AIRoundtableApp();
});

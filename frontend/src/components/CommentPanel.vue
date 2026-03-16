<template>
  <div class="comment-panel">
    <!-- Tab 切换（可选展示"全部评论"） -->
    <a-space v-if="showAllTab" style="margin-bottom: 12px">
      <a-button :type="tab === 'node' ? 'primary' : 'default'" size="small"
        @click="switchTab('node')">当前节点</a-button>
      <a-button :type="tab === 'all' ? 'primary' : 'default'" size="small"
        @click="switchTab('all')">全部评论</a-button>
    </a-space>

    <!-- 评论列表 -->
    <div class="comments-section">
      <div v-if="!currentList.length" class="empty-hint">暂无评论</div>

      <div v-for="c in currentList" :key="c.id" class="cmt-card">
        <!-- 全部评论模式下显示节点内容链接 -->
        <div v-if="c.nodeText && tab === 'all'" class="cmt-node-link"
          @click="$emit('navigate', c.nodeId)">
          <span style="margin-right: 4px">📍</span>{{ c.nodeText }}
        </div>

        <!-- 根评论 -->
        <div class="cmt-main">
          <div class="cmt-avatar">{{ (c.displayName || '?')[0] }}</div>
          <div class="cmt-content">
            <div class="cmt-meta">
              <strong>{{ c.displayName }}</strong>
              <div class="cmt-time">{{ fmt(c.createdAt) }}</div>
            </div>
            <div v-if="editingId !== c.id" class="cmt-text">{{ c.content }}</div>
            <div v-else style="margin-top: 4px">
              <a-textarea v-model:value="editingContent" :auto-size="{ minRows: 1, maxRows: 4 }" />
              <a-space style="margin-top: 4px">
                <a-button size="small" type="primary" @click="saveEdit(c)">保存</a-button>
                <a-button size="small" @click="cancelEdit">取消</a-button>
              </a-space>
            </div>
            <div class="cmt-actions">
              <span @click="startReply(c)">回复</span>
              <span @click="startEdit(c)">编辑</span>
              <a-popconfirm title="确定删除?" @confirm="doDelete(c.id, c.nodeId)">
                <span class="danger">删除</span>
              </a-popconfirm>
              <span v-if="!c.resolved" class="resolve-btn" @click="doResolve(c)">标记解决</span>
              <span v-else class="resolved" @click="doResolve(c)">已解决</span>
            </div>
          </div>
        </div>

        <!-- 子回复 -->
        <div v-if="c.replies?.length" class="cmt-replies">
          <div v-for="r in c.replies" :key="r.id" class="cmt-main cmt-reply">
            <div class="cmt-avatar sm">{{ (r.displayName || '?')[0] }}</div>
            <div class="cmt-content">
              <div class="cmt-meta">
                <div class="cmt-name-line">
                  <strong>{{ r.displayName }}</strong>
                  <!-- "回复 @XXX" 固定显示在名字后，不可编辑 -->
                  <span v-if="parseReply(r.content).replyTo" class="reply-label">
                    {{ parseReply(r.content).replyTo }}
                  </span>
                </div>
                <div class="cmt-time">{{ fmt(r.createdAt) }}</div>
              </div>
              <!-- 只显示/编辑纯文字内容（不含 @mention 前缀） -->
              <div v-if="editingId !== r.id" class="cmt-text">{{ parseReply(r.content).text }}</div>
              <div v-else style="margin-top: 4px">
                <a-textarea v-model:value="editingContent" :auto-size="{ minRows: 1, maxRows: 4 }" />
                <a-space style="margin-top: 4px">
                  <a-button size="small" type="primary" @click="saveEdit(r)">保存</a-button>
                  <a-button size="small" @click="cancelEdit">取消</a-button>
                </a-space>
              </div>
              <div class="cmt-actions">
                <span @click="startReply(c, r)">回复</span>
                <span @click="startEdit(r)">编辑</span>
                <a-popconfirm title="确定删除?" @confirm="doDelete(r.id, c.nodeId)">
                  <span class="danger">删除</span>
                </a-popconfirm>
              </div>
            </div>
          </div>
        </div>

        <!-- 回复输入框 -->
        <div v-if="replyingTo?.id === c.id" class="cmt-reply-box">
          <div v-if="replyTarget" class="reply-hint">回复 @{{ replyTarget.displayName }}</div>
          <a-textarea v-model:value="replyContent" :placeholder="replyPlaceholder"
            :auto-size="{ minRows: 1, maxRows: 3 }"
            @pressEnter.exact.prevent="submitReply(c)" />
          <a-space style="margin-top: 4px">
            <a-button size="small" type="primary" :loading="locks.submitReply" @click="submitReply(c)">发送</a-button>
            <a-button size="small" @click="cancelReply">取消</a-button>
          </a-space>
        </div>
      </div>
    </div>

    <!-- 当前节点输入框 -->
    <div v-if="tab === 'node' && nodeId" class="cmt-input-area">
      <a-textarea v-model:value="newComment" placeholder="输入评论，回车发送..."
        :auto-size="{ minRows: 2, maxRows: 4 }" @pressEnter.exact.prevent="addComment" />
      <a-button type="primary" size="small" style="margin-top: 4px" :loading="locks.addComment" @click="addComment">发送</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { commentApi } from '../api';
import { useGuard } from '../composables/useGuard';

const props = defineProps<{
  nodeId: string | null;          // 当前选中节点 ID
  caseSetId: string;              // 用例集 ID
  showAllTab?: boolean;           // 是否展示"全部评论"Tab
}>();

const emit = defineEmits<{
  (e: 'navigate', nodeId: string): void;          // 定位到节点
  (e: 'count-changed', nodeId: string, count: number): void; // 评论数变化
}>();

// ── 状态 ──────────────────────────────────────────
const tab = ref<'node' | 'all'>('node');
const nodeComments = ref<any[]>([]);
const allComments = ref<any[]>([]);
const newComment = ref('');
const replyingTo = ref<any>(null);   // 要回复的根评论
const replyTarget = ref<any>(null);  // 实际点击回复的目标（可能是子回复，用于 @mention）
const replyContent = ref('');
const editingId = ref<string | null>(null);
const editingContent = ref('');
const editingReplyPrefix = ref<string | null>(null); // 编辑子回复时保留 @mention 前缀
const { locks, run } = useGuard();

const currentList = computed(() =>
  tab.value === 'node' ? nodeComments.value : allComments.value
);
const replyPlaceholder = computed(() =>
  replyTarget.value ? `回复 @${replyTarget.value.displayName}...` : '回复...'
);

function fmt(t: string) {
  if (!t) return '';
  return t.replace('T', ' ').substring(0, 19);
}

/**
 * 解析回复内容：
 * 格式 "回复 @XXX: 实际文字" → { replyTo: "回复 @XXX", text: "实际文字" }
 * 普通内容 → { replyTo: null, text: 原内容 }
 */
function parseReply(content: string): { replyTo: string | null; text: string } {
  const match = content?.match(/^(回复 @[^:]+): ([\s\S]*)$/);
  if (match) return { replyTo: match[1], text: match[2] };
  return { replyTo: null, text: content || '' };
}

// ── 数据加载 ──────────────────────────────────────
async function loadNodeComments() {
  if (!props.nodeId) { nodeComments.value = []; return; }
  nodeComments.value = (await commentApi.nodeComments(props.nodeId)).data;
}

async function loadAllComments() {
  allComments.value = (await commentApi.allComments(props.caseSetId)).data;
}

function load() {
  if (tab.value === 'node') loadNodeComments();
  else loadAllComments();
}

async function refreshCount(nodeId: string) {
  try {
    const res = await commentApi.unresolvedCount(nodeId);
    emit('count-changed', nodeId, res.data);
  } catch { /* ignore */ }
}

// ── Watch ─────────────────────────────────────────
watch(() => props.nodeId, () => {
  if (tab.value === 'node') loadNodeComments();
}, { immediate: true });

watch(tab, (val) => {
  if (val === 'node') loadNodeComments();
  else loadAllComments();
});

function switchTab(t: 'node' | 'all') {
  tab.value = t;
}

// 外部调用：强制刷新（例如面板切换时）
function refresh() { load(); }
defineExpose({ refresh, switchTab });

// ── 回复 ─────────────────────────────────────────
function startReply(rootComment: any, target?: any) {
  replyingTo.value = rootComment;
  replyTarget.value = target || null;
  replyContent.value = '';
}
function cancelReply() {
  replyingTo.value = null;
  replyTarget.value = null;
  replyContent.value = '';
}
async function submitReply(parent: any) {
  if (!replyContent.value.trim()) return;
  await run('submitReply', async () => {
    const content = replyTarget.value
      ? `回复 @${replyTarget.value.displayName}: ${replyContent.value.trim()}`
      : replyContent.value.trim();
    const nodeId = parent.nodeId || props.nodeId || '';
    await commentApi.add(nodeId, props.caseSetId, content, parent.id);
    cancelReply();
    load();
    if (nodeId) refreshCount(nodeId);
  });
}

// ── 新增评论 ─────────────────────────────────────
async function addComment() {
  if (!newComment.value.trim() || !props.nodeId) return;
  await run('addComment', async () => {
    await commentApi.add(props.nodeId!, props.caseSetId, newComment.value.trim());
    newComment.value = '';
    loadNodeComments();
    refreshCount(props.nodeId!);
  });
}

// ── 编辑 ─────────────────────────────────────────
function startEdit(c: any) {
  editingId.value = c.id;
  const parsed = parseReply(c.content);
  // 子回复有 @mention 前缀时，只让用户编辑纯文字部分
  editingReplyPrefix.value = parsed.replyTo;
  editingContent.value = parsed.text;
}
function cancelEdit() {
  editingId.value = null;
  editingContent.value = '';
  editingReplyPrefix.value = null;
}
async function saveEdit(c: any) {
  if (!editingId.value || !editingContent.value.trim()) return;
  await run('saveEdit', async () => {
    const content = editingReplyPrefix.value
      ? `${editingReplyPrefix.value}: ${editingContent.value.trim()}`
      : editingContent.value.trim();
    await commentApi.update(editingId.value!, content);
    cancelEdit();
    load();
  });
}

// ── 删除 ─────────────────────────────────────────
async function doDelete(id: string, nodeId?: string) {
  await run('doDelete', async () => {
    await commentApi.delete(id);
    load();
    const nid = nodeId || props.nodeId;
    if (nid) refreshCount(nid);
  });
}

// ── 标记解决 ─────────────────────────────────────
async function doResolve(c: any) {
  await run('doResolve', async () => {
    await commentApi.resolve(c.id);
    load();
    const nodeId = c.nodeId || props.nodeId;
    if (nodeId) refreshCount(nodeId);
  });
}
</script>

<style scoped>
.comment-panel { display: flex; flex-direction: column; height: 100%; }
.comments-section { flex: 1; overflow-y: auto; }

.cmt-card { padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.cmt-main { display: flex; gap: 8px; align-items: flex-start; }
.cmt-avatar {
  width: 32px; height: 32px; border-radius: 50%; background: #1677ff;
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600; flex-shrink: 0;
}
.cmt-avatar.sm { width: 24px; height: 24px; font-size: 11px; }
.cmt-content { flex: 1; min-width: 0; }
.cmt-meta { display: flex; flex-direction: column; margin-bottom: 2px; }
.cmt-name-line { display: flex; align-items: baseline; gap: 4px; flex-wrap: wrap; }
.cmt-meta strong { font-size: 13px; color: #262626; }
.reply-label { font-size: 12px; color: #888; white-space: nowrap; }
.cmt-time { font-size: 11px; color: #aaa; margin-top: 1px; }
.cmt-text { font-size: 13px; color: #595959; word-break: break-all; line-height: 1.5; }
.cmt-actions { display: flex; gap: 10px; margin-top: 4px; }
.cmt-actions span { font-size: 12px; color: #888; cursor: pointer; }
.cmt-actions span:hover { color: #1677ff; }
.cmt-actions .danger { color: #ff4d4f; }
.cmt-actions .danger:hover { color: #ff7875; }
.resolve-btn { color: #1677ff !important; }
.resolved { color: #52c41a !important; }

.cmt-replies { margin-left: 40px; margin-top: 6px; }
.cmt-reply { margin-top: 6px; }
.cmt-reply-box { padding-left: 40px; margin-top: 6px; }
.reply-hint {
  font-size: 12px; color: #888; margin-bottom: 4px;
  padding: 2px 6px; background: #f5f5f5; border-radius: 3px; display: inline-block;
}

.cmt-node-link {
  font-size: 12px; color: #1677ff; cursor: pointer; margin-bottom: 6px;
  padding: 3px 6px; background: #e6f4ff; border-radius: 4px;
  overflow: hidden; text-overflow: ellipsis; display: -webkit-box;
  -webkit-line-clamp: 3; -webkit-box-orient: vertical;
}
.cmt-node-link:hover { text-decoration: underline; }

.empty-hint { text-align: center; color: #bbb; padding: 32px 0; font-size: 13px; }

.cmt-input-area { border-top: 1px solid #f0f0f0; padding-top: 8px; margin-top: 8px; }
</style>

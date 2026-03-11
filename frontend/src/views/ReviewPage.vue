<template>
  <a-layout style="height: 100vh">
    <a-layout-header class="review-header">
      <a-space>
        <a-button type="text" @click="$router.push('/cases')"><ArrowLeftOutlined /></a-button>
        <a-tag color="warning">评审中</a-tag>
        <strong>{{ caseSet?.name }}</strong>
      </a-space>
      <a-space>
        <a-button v-if="myReview" type="primary" @click="showReviewDrawer = true"><CheckCircleOutlined /> 评审结果</a-button>
        <a-button @click="showComments = true"><CommentOutlined /> 全部评论</a-button>
      </a-space>
    </a-layout-header>
    <a-layout style="flex: 1; overflow: hidden">
      <a-layout-content style="position: relative">
        <div ref="mindMapContainer" style="width: 100%; height: 100%"></div>
      </a-layout-content>
      <div v-if="selectedNodeRaw" class="review-side">
        <h4>节点标记</h4>
        <a-select :value="(selectedNodeRaw.properties?.mark as string) || 'NONE'" @change="(v: any) => handleMark(v)" style="width: 100%"
          :options="[{value:'NONE',label:'无'},{value:'PENDING',label:'待完成'},{value:'TO_CONFIRM',label:'待确认'},{value:'TO_MODIFY',label:'待修改'}]" />
        <a-divider />
        <h4>评论</h4>
        <a-list size="small" :data-source="nodeComments" :locale="{ emptyText: '暂无' }">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta :description="item.content">
                <template #title><strong>{{ item.displayName }}</strong> <span style="color:#999;font-size:11px">{{ item.createdAt }}</span></template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
        <a-textarea v-model:value="newComment" placeholder="输入评论..." :auto-size="{ minRows: 2 }" style="margin-top: 8px" />
        <a-button type="primary" size="small" style="margin-top: 8px" @click="addComment">发送</a-button>
      </div>
    </a-layout>
    <a-drawer :open="showReviewDrawer" @close="showReviewDrawer = false" title="评审结果" :width="360">
      <a-list :data-source="reviewers">
        <template #renderItem="{ item }">
          <a-list-item>
            <span>{{ getUserName(item.reviewerId) }}</span>
            <template #actions>
              <a-select v-if="item.reviewerId === store.user?.id" :value="item.status" @change="(v: any) => updateReview(item.id, v)" size="small" style="width: 110px"
                :options="[{value:'PENDING',label:'未评审'},{value:'APPROVED',label:'通过'},{value:'REJECTED',label:'不通过'},{value:'NEED_MODIFY',label:'待修改'}]" />
              <a-tag v-else>{{ item.status }}</a-tag>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-drawer>
    <a-drawer :open="showComments" @close="showComments = false" title="全部评论" :width="360">
      <a-list :data-source="allComments" :locale="{ emptyText: '暂无评论' }">
        <template #renderItem="{ item }">
          <a-list-item><a-list-item-meta :description="item.content"><template #title>{{ item.displayName }} - 节点#{{ item.nodeId }}</template></a-list-item-meta></a-list-item>
        </template>
      </a-list>
    </a-drawer>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
import { ArrowLeftOutlined, CheckCircleOutlined, CommentOutlined } from '@ant-design/icons-vue';
import MindMap from 'simple-mind-map';
import { caseSetApi, mindNodeApi, commentApi, reviewApi, userApi } from '../api';
import { useAppStore } from '../stores/app';
import type { CaseSet, MindNodeData, CommentData, ReviewAssignment, User } from '../types';

const route = useRoute();
const store = useAppStore();
const id = String(route.params.caseSetId);

const mindMapContainer = ref<HTMLDivElement | null>(null);
let mindMapInstance: any = null;
const caseSet = ref<CaseSet | null>(null);
const reviewers = ref<ReviewAssignment[]>([]);
const users = ref<User[]>([]);
const selectedNodeRaw = ref<any>(null);
const nodeComments = ref<CommentData[]>([]);
const allComments = ref<CommentData[]>([]);
const newComment = ref('');
const showReviewDrawer = ref(false);
const showComments = ref(false);

const myReview = computed(() => reviewers.value.find(r => r.reviewerId === store.user?.id));
function getUserName(uid: string) { return users.value.find(u => u.id === uid)?.displayName || `用户${uid}`; }

function nodeToMM(node: MindNodeData): any {
  const t = node.nodeType ? `[${node.nodeType}] ` : '';
  return { data: { text: t + node.text, uid: String(node.id), _raw: node }, children: (node.children || []).map(c => nodeToMM(c)) };
}

onMounted(async () => {
  const [csRes, treeRes, revRes] = await Promise.all([caseSetApi.get(id), mindNodeApi.tree(id), reviewApi.list(id)]);
  caseSet.value = csRes.data;
  reviewers.value = revRes.data;
  users.value = (await userApi.listAll()).data;
  await nextTick();
  mindMapInstance = new MindMap({
    el: mindMapContainer.value!, data: treeRes.data.length ? nodeToMM(treeRes.data[0]) : { data: { text: '空' }, children: [] },
    theme: 'classic4', layout: 'logicalStructure', readonly: true, mousewheelAction: 'zoom',
  });
  mindMapInstance.on('node_active', (_: any, nodes: any[]) => {
    if (nodes.length) {
      selectedNodeRaw.value = nodes[0].data?._raw || null;
      if (selectedNodeRaw.value?.id) loadNodeComments();
    } else { selectedNodeRaw.value = null; }
  });
});
onUnmounted(() => { if (mindMapInstance) mindMapInstance.destroy(); });

async function handleMark(mark: string) {
  if (!selectedNodeRaw.value?.id) return;
  await mindNodeApi.update(selectedNodeRaw.value.id, { properties: { ...(selectedNodeRaw.value.properties || {}), mark } });
  if (!selectedNodeRaw.value.properties) selectedNodeRaw.value.properties = {};
  selectedNodeRaw.value.properties.mark = mark;
  message.success('标记已更新');
}
async function loadNodeComments() {
  if (!selectedNodeRaw.value?.id) return;
  nodeComments.value = (await commentApi.nodeComments(selectedNodeRaw.value.id)).data;
}
async function addComment() {
  if (!newComment.value.trim() || !selectedNodeRaw.value?.id) return;
  await commentApi.add(selectedNodeRaw.value.id, id, newComment.value.trim());
  newComment.value = ''; loadNodeComments();
}
async function updateReview(rid: string, status: string) {
  await reviewApi.updateStatus(rid, status);
  reviewers.value = (await reviewApi.list(id)).data;
  message.success('评审状态已更新');
}
</script>

<style scoped>
.review-header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #f0f0f0; padding: 0 16px; height: 48px; line-height: 48px; }
.review-side { width: 300px; background: #fff; border-left: 1px solid #f0f0f0; padding: 16px; overflow-y: auto; flex-shrink: 0; }
</style>

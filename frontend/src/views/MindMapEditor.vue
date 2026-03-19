<template>
  <a-layout style="height: 100vh; overflow: hidden">
    <!-- 顶部工具栏 -->
    <div class="editor-header">
      <a-space>
        <a-button type="text" @click="$router.push('/cases')"><ArrowLeftOutlined /></a-button>
        <a-dropdown>
          <a-tag :color="statusColor" style="cursor: pointer">{{ statusLabel }}</a-tag>
          <template #overlay>
            <a-menu @click="(info: any) => handleStatusChange(info.key)">
              <a-menu-item key="WRITING">编写中</a-menu-item>
              <a-menu-item key="PENDING_REVIEW">待评审</a-menu-item>
              <a-menu-item key="NO_REVIEW">无需评审</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <!-- 用例集名称：双击进入编辑，回车/失焦保存 -->
        <span v-if="!editingCaseSetName" style="font-weight:600;cursor:pointer;" @dblclick="startEditCaseSetName"
          title="双击编辑名称">{{ caseSet?.name }}</span>
        <a-input v-else v-model:value="caseSetNameInput" size="small" style="width:200px;font-weight:600"
          @pressEnter="saveCaseSetName" @blur="saveCaseSetName" @keyup.escape="editingCaseSetName = false" autofocus />
        <span style="color: #999; font-size: 12px">({{ caseCount }}条用例)</span>
        <a v-if="caseSet?.requirementLink" :href="caseSet.requirementLink" target="_blank"
          style="color:#1677ff;font-size:12px;text-decoration:none;margin-left:4px" @click.stop>关联需求</a>
      </a-space>
      <a-space>
        <a-tooltip title="添加子节点(Tab)"><a-button type="text" :disabled="mindMapLoading" @click="debouncedExecCmd('INSERT_CHILD_NODE')"><PlusOutlined /></a-button></a-tooltip>
        <a-tooltip title="添加同级(Enter)"><a-button type="text" :disabled="mindMapLoading" @click="debouncedExecCmd('INSERT_NODE')"><PlusSquareOutlined /></a-button></a-tooltip>
        <a-tooltip title="删除(Delete)"><a-button type="text" danger :disabled="mindMapLoading" @click="debouncedExecCmd('REMOVE_NODE')"><DeleteOutlined /></a-button></a-tooltip>
        <a-divider type="vertical" />
        <a-tooltip title="复制选中节点(Ctrl+C)"><a-button type="text" :disabled="mindMapLoading" @click="copySelectedNodes"><CopyOutlined /></a-button></a-tooltip>
        <a-tooltip title="粘贴到当前节点(Ctrl+V)"><a-button type="text" :disabled="mindMapLoading" @click="pasteNodes"><SnippetsOutlined /></a-button></a-tooltip>
        <a-divider type="vertical" />
        <a-tooltip title="全部展开"><a-button type="text" :disabled="mindMapLoading" @click="debouncedExecCmd('EXPAND_ALL')"><NodeExpandOutlined /></a-button></a-tooltip>
        <a-tooltip title="全部折叠"><a-button type="text" :disabled="mindMapLoading" @click="debouncedExecCmd('UNEXPAND_ALL')"><NodeCollapseOutlined /></a-button></a-tooltip>
        <a-divider type="vertical" />
        <a-tooltip title="查找替换"><a-button type="text" @click="toggleSearch"><SearchOutlined /></a-button></a-tooltip>
        <a-tooltip title="规范检查"><a-button type="text" @click="openValidation"><ToolOutlined /></a-button></a-tooltip>
        <a-tooltip title="评论"><a-button type="text" @click="openComments"><CommentOutlined /></a-button></a-tooltip>
        <a-tooltip title="导出Excel"><a-button type="text" :loading="locks.exportExcel" @click="handleExportExcel"><DownloadOutlined /></a-button></a-tooltip>
        <a-tooltip title="导入Excel"><a-button type="text" @click="triggerImportExcel"><UploadOutlined /></a-button></a-tooltip>
        <input ref="importFileInput" type="file" accept=".xlsx,.xls" style="display:none" @change="handleImportExcel" />
        <span v-if="hasUnsavedChanges" style="color:#e6a23c;font-size:11px;margin-right:4px">有未保存的更改</span>
        <a-button type="primary" :loading="saving" :disabled="saving || mindMapLoading" @click="handleSave"><SaveOutlined /> 同步云端</a-button>
        <a-tooltip title="历史版本"><a-button type="text" @click="openHistory"><HistoryOutlined /></a-button></a-tooltip>
      </a-space>
    </div>

    <!-- 查找替换栏 -->
    <div v-if="showSearch" class="search-bar">
      <a-input size="small" v-model:value="searchText" placeholder="查找" style="width: 150px"
        @pressEnter="findNext" @change="onSearchChange" />
      <a-input size="small" v-model:value="replaceText" placeholder="替换" style="width: 150px" />
      <a-button size="small" @click="findPrev">上一个</a-button>
      <a-button size="small" @click="findNext">下一个</a-button>
      <a-button size="small" @click="replaceOneFn">替换</a-button>
      <a-button size="small" @click="replaceAllFn">全部替换</a-button>
      <span v-if="searchMatches.length" style="color: #666; font-size: 12px; min-width: 50px">
        {{ searchIdx + 1 }}/{{ searchMatches.length }}
      </span>
      <a-button size="small" type="text" @click="showSearch = false; searchMatches = []">×</a-button>
    </div>

    <!-- 主体: 思维导图 + 可选右侧面板 -->
    <div class="editor-body">
      <!-- 思维导图区域 -->
      <div class="mm-area">
        <div ref="mindMapContainer" style="width: 100%; height: 100%"></div>

        <!-- 加载遮罩 -->
        <div v-if="mindMapLoading" class="mm-loading-overlay">
          <a-spin size="large" />
          <span style="margin-top:12px;color:#666;font-size:13px">加载思维导图中…</span>
        </div>

        <!-- 水平滚动条 -->
        <div ref="hScrollRef" class="mm-scrollbar mm-scrollbar-h"
          @click="(e: MouseEvent) => mindMapInstance?.scrollbar?.onClick(e, 'horizontal')">
          <div class="mm-scrollbar-thumb"
            :style="{ left: scrollbarH.left + '%', width: scrollbarH.width + '%' }"
            @mousedown.stop="(e: MouseEvent) => mindMapInstance?.scrollbar?.onMousedown(e, 'horizontal')"></div>
        </div>

        <!-- 垂直滚动条 -->
        <div ref="vScrollRef" class="mm-scrollbar mm-scrollbar-v"
          @click="(e: MouseEvent) => mindMapInstance?.scrollbar?.onClick(e, 'vertical')">
          <div class="mm-scrollbar-thumb"
            :style="{ top: scrollbarV.top + '%', height: scrollbarV.height + '%' }"
            @mousedown.stop="(e: MouseEvent) => mindMapInstance?.scrollbar?.onMousedown(e, 'vertical')"></div>
        </div>

        <!-- 右下角缩放控件 -->
        <div class="mm-zoom-bar">
          <button class="mm-zoom-btn" @click="zoomOut" title="缩小">−</button>
          <span class="mm-zoom-label" @click="zoomReset" title="重置缩放">{{ zoomLevel }}%</span>
          <button class="mm-zoom-btn" @click="zoomIn" title="放大">+</button>
        </div>

        <div class="tips-bar">
          左键点选 · 左键拖拽框选 · 右键拖拽平移 · 滚轮上下滑动 · Ctrl+滚轮缩放 · 双击编辑
        </div>
      </div>

      <!-- 右侧面板 (按需出现) -->
      <div v-if="rightPanelOpen" class="right-panel">
        <div class="rp-header">
          <a-radio-group v-model:value="panelTab" button-style="solid" size="small">
            <a-radio-button value="props">属性</a-radio-button>
            <a-radio-button value="validate">检查</a-radio-button>
            <a-radio-button value="comments">评论</a-radio-button>
          </a-radio-group>
          <a-button size="small" type="text" @click="closePanel"><CloseOutlined /></a-button>
        </div>

        <div class="rp-body">
          <!-- ===== 属性 Tab ===== -->
          <template v-if="panelTab === 'props'">
            <template v-if="editForm">
              <div class="prop-field">
                <label>内容</label>
                <a-textarea v-model:value="editForm.text" :auto-size="{ minRows: 2 }" placeholder="节点文本"
                  @blur="syncToNode()" @pressEnter.exact.prevent="syncToNode()" />
              </div>
              <div class="prop-field">
                <label>节点类型</label>
                <a-select v-model:value="editForm.nodeType" allow-clear @change="syncToNode(true)"
                  :options="nodeTypeOptions" style="width: 100%" placeholder="可不设置" />
              </div>
              <div class="prop-field">
                <label>标记</label>
                <a-select :value="editForm?.properties?.mark || 'NONE'" @change="(v: any) => { if (editForm) { editForm.properties.mark = v === 'NONE' ? undefined : v; syncToNode(); } }" style="width: 100%"
                  :options="[{value:'NONE',label:'无'},{value:'待完成',label:'待完成'},{value:'待确认',label:'待确认'},{value:'待修改',label:'待修改'}]" />
              </div>
              <a-divider style="margin: 8px 0" />
              <div style="color: #1677ff; font-weight: 600; font-size: 12px; margin-bottom: 8px">动态属性</div>
              <template v-for="attr in filteredEditAttrs" :key="attr.id">
                <div class="prop-field">
                  <label>{{ attr.name }}</label>
                  <!-- 单选 + 平铺 -->
                  <div v-if="attr.displayType === 'TILE' && !attr.multiSelect" class="tile-group">
                    <span v-for="o in attr.options" :key="o"
                      class="tile-tag"
                      :class="[editForm?.properties[attr.name] === o ? 'active' : '', attr.name === '优先级' ? `priority-${o.toLowerCase()}` : '']"
                      @click="toggleSingleTileTag(attr.name, o)">{{ o }}</span>
                  </div>
                  <!-- 多选 + 平铺 -->
                  <div v-else-if="attr.multiSelect && attr.displayType === 'TILE'" class="tile-group">
                    <span v-for="o in attr.options" :key="o"
                      class="tile-tag"
                      :class="{ active: (editForm?.properties[attr.name] || []).includes(o) }"
                      @click="toggleTileTag(attr.name, o)">{{ o }}</span>
                  </div>
                  <!-- 多选 + 下拉 -->
                  <a-select v-else-if="attr.multiSelect" mode="multiple"
                    :value="Array.isArray(editForm?.properties[attr.name]) ? editForm?.properties[attr.name] : []"
                    @change="(v: any) => { if (editForm) { editForm.properties[attr.name] = v; syncToNode(); } }"
                    :options="attr.options.map((o: string) => ({ value: o, label: o }))" style="width: 100%" />
                  <!-- 单选 + 下拉 -->
                  <a-select v-else
                    :value="editForm?.properties[attr.name] || undefined"
                    @change="(v: any) => { if (editForm) { editForm.properties[attr.name] = v; syncToNode(); } }"
                    allow-clear :options="attr.options.map((o: string) => ({ value: o, label: o }))" style="width: 100%" />
                </div>
              </template>

              <!-- 子孙节点可设置的属性 -->
              <template v-if="descendantAttrs.length">
                <a-divider style="margin: 8px 0" />
                <div style="color: #722ed1; font-weight: 600; font-size: 12px; margin-bottom: 4px">批量设置子孙属性</div>
                <div style="color: #999; font-size: 11px; margin-bottom: 8px">选择后将应用到所有符合类型的子孙节点</div>
                <template v-for="dattr in descendantAttrs" :key="dattr.id">
                  <div class="prop-field">
                    <label>{{ dattr.name }} <span style="color:#722ed1;font-size:10px">({{ (dattr.nodeTypeLimit || '').split(',').map((t: string) => NODE_TYPE_LABEL[t] || t).join('、') }})</span></label>
                    <!-- 单选 + 平铺 -->
                    <div v-if="dattr.displayType === 'TILE' && !dattr.multiSelect" class="tile-group">
                      <span v-for="o in dattr.options" :key="o" class="tile-tag desc-tile"
                        :class="{ active: descTileValues[dattr.name] === o }"
                        @click="toggleDescSingleTile(dattr, o)">{{ o }}</span>
                    </div>
                    <!-- 多选 + 平铺 -->
                    <div v-else-if="dattr.multiSelect && dattr.displayType === 'TILE'" class="tile-group">
                      <span v-for="o in dattr.options" :key="o" class="tile-tag desc-tile"
                        :class="{ active: (descTileValues[dattr.name] || []).includes(o) }"
                        @click="toggleDescMultiTile(dattr, o)">{{ o }}</span>
                    </div>
                    <!-- 多选 + 下拉 -->
                    <a-select v-else-if="dattr.multiSelect" mode="multiple" :value="[]"
                      @change="(v: any) => applyToDescendants(dattr, v)"
                      :options="dattr.options.map((o: string) => ({ value: o, label: o }))" style="width: 100%"
                      placeholder="选择后批量设置" />
                    <!-- 单选 + 下拉 -->
                    <a-select v-else :value="undefined"
                      @change="(v: any) => applyToDescendants(dattr, v)"
                      allow-clear :options="dattr.options.map((o: string) => ({ value: o, label: o }))" style="width: 100%"
                      placeholder="选择后批量设置" />
                  </div>
                </template>
              </template>
            </template>
            <div v-else class="empty-hint">选择节点查看属性</div>
          </template>

          <!-- ===== 规范检查 Tab ===== -->
          <template v-if="panelTab === 'validate'">
            <a-button type="primary" size="small" block style="margin-bottom: 12px" @click="runValidation">
              <ToolOutlined /> 开始检查
            </a-button>
            <div v-if="!valErrors.length && valChecked" style="text-align: center; padding: 16px; color: #52c41a">
              <CheckCircleOutlined style="font-size: 24px" />
              <p>所有分支符合规范</p>
            </div>
            <div v-if="valErrors.length">
              <div style="color: #ff4d4f; font-size: 12px; margin-bottom: 8px">
                共{{ valErrors.length }}条错误，点击定位到分支末尾节点
              </div>
              <div class="val-list">
                <div v-for="(err, idx) in valErrors" :key="idx"
                  class="val-item" :class="{ active: valIdx === idx }" @click="navigateToError(idx)">
                  <span class="val-idx">{{ idx + 1 }}</span>
                  <span class="val-msg">{{ err.message }}</span>
                </div>
              </div>
            </div>
          </template>

          <!-- ===== 评论 Tab ===== -->
          <template v-if="panelTab === 'comments'">
            <CommentPanel
              ref="commentPanelRef"
              :node-id="activeNodeInstance?.nodeData?.data?._raw?.id ?? null"
              :case-set-id="caseSetId"
              :show-all-tab="true"
              @navigate="navigateToCommentNode"
              @count-changed="onCommentCountChanged"
            />
          </template>
        </div>
      </div>
    </div>

    <!-- 历史版本抽屉 -->
    <a-drawer :open="showHistory" @close="showHistory = false" title="历史版本" :width="360">
      <a-list :data-source="versions" :locale="{ emptyText: '暂无历史版本' }">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta :title="`版本 ${item.id.substring(0, 8)}`" :description="item.createdAt" />
            <template #actions>
              <a-popconfirm title="确认恢复?" @confirm="restoreVersion(item.id)">
                <a-button type="link" size="small" :loading="restoringVersion">恢复</a-button>
              </a-popconfirm>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-drawer>

    <!-- 评审人弹窗 -->
    <a-modal v-model:open="showReviewModal" title="选择评审人" @ok="submitReview" :confirmLoading="submittingReview">
      <a-select mode="multiple" style="width: 100%" v-model:value="selectedReviewers" placeholder="选择评审人"
        :options="users.map((u: any) => ({ value: u.id, label: u.displayName }))" />
    </a-modal>

    <!-- 退出确认弹窗 -->
    <a-modal v-model:open="showExitConfirm" title="有未保存的更改" :closable="false" :footer="null" :maskClosable="false">
      <p style="margin: 12px 0 20px; color: #666">当前页面有未保存的内容，是否保存后退出？</p>
      <div style="display: flex; justify-content: flex-end; gap: 8px">
        <a-button @click="handleExitCancel">取消</a-button>
        <a-button danger @click="handleExitDiscard">不保存退出</a-button>
        <a-button type="primary" @click="handleExitSave">保存退出</a-button>
      </div>
    </a-modal>

    <!-- 版本冲突解决弹窗 -->
    <a-modal v-model:open="showConflictDialog" title="版本冲突" :closable="false" :footer="null" :maskClosable="false" width="640px">
      <div style="padding: 8px 0">
        <a-alert type="warning" show-icon style="margin-bottom:16px">
          <template #message>
            <span>云端已有更新的版本 (v{{ conflictServerVersion }})，您的本地版本 (v{{ dataVersion }}) 已过期。请选择如何处理：</span>
          </template>
        </a-alert>
        <div style="display:flex;gap:12px;margin-top:16px;flex-wrap:wrap">
          <div class="conflict-card conflict-local" @click="resolveConflictUseLocal">
            <div class="conflict-card-title">使用本地版本</div>
            <div class="conflict-card-desc">丢弃云端更改，使用您当前编辑的内容覆盖云端。</div>
          </div>
          <div class="conflict-card conflict-server" @click="resolveConflictUseServer">
            <div class="conflict-card-title">使用云端版本</div>
            <div class="conflict-card-desc">丢弃本地更改，使用云端最新版本的内容。</div>
          </div>
          <div class="conflict-card conflict-merge" @click="openNodeDiff" style="flex-basis:100%">
            <div class="conflict-card-title">逐节点对比合并</div>
            <div class="conflict-card-desc">逐个对比有差异的节点，选择保留本地或云端内容，合并后上传。</div>
          </div>
        </div>
      </div>
    </a-modal>

    <!-- 逐节点对比弹窗 -->
    <a-modal v-model:open="showNodeDiffDialog" title="节点差异对比" :closable="true" :footer="null"
      :maskClosable="false" width="900px" :bodyStyle="{ maxHeight: '70vh', overflow: 'auto' }">
      <div v-if="diffNodes.length === 0" style="text-align:center;padding:40px;color:#52c41a">
        <CheckCircleOutlined style="font-size:32px" />
        <p style="margin-top:8px">没有发现差异节点</p>
      </div>
      <template v-else>
        <div style="margin-bottom:12px;display:flex;justify-content:space-between;align-items:center">
          <span style="color:#666;font-size:13px">共 <b>{{ diffNodes.length }}</b> 个差异节点，当前第 <b>{{ diffCurrentIdx + 1 }}</b> 个</span>
          <a-space>
            <a-button size="small" @click="diffChooseAllLocal">全部使用本地</a-button>
            <a-button size="small" @click="diffChooseAllServer">全部使用云端</a-button>
          </a-space>
        </div>
        <div class="diff-progress">
          <div v-for="(d, i) in diffNodes" :key="d.nodeId" class="diff-dot"
            :class="{ active: i === diffCurrentIdx, chosen: d.choice !== null, local: d.choice === 'local', server: d.choice === 'server' }"
            @click="diffCurrentIdx = i" :title="d.localText || d.serverText || '节点'">
          </div>
        </div>
        <div v-if="diffNodes[diffCurrentIdx]" class="diff-panel">
          <div class="diff-col" :class="{ selected: diffNodes[diffCurrentIdx].choice === 'local' }"
            @click="diffNodes[diffCurrentIdx].choice = 'local'">
            <div class="diff-col-header local">本地版本</div>
            <div class="diff-col-body">
              <div class="diff-field"><span class="diff-label">文本：</span>{{ diffNodes[diffCurrentIdx].localText || '(空)' }}</div>
              <div class="diff-field"><span class="diff-label">类型：</span>{{ ntLabelFn(diffNodes[diffCurrentIdx].localType) }}</div>
              <div v-if="diffNodes[diffCurrentIdx].localPropsStr" class="diff-field"><span class="diff-label">属性：</span>{{ diffNodes[diffCurrentIdx].localPropsStr }}</div>
            </div>
          </div>
          <div class="diff-col" :class="{ selected: diffNodes[diffCurrentIdx].choice === 'server' }"
            @click="diffNodes[diffCurrentIdx].choice = 'server'">
            <div class="diff-col-header server">云端版本</div>
            <div class="diff-col-body">
              <div class="diff-field"><span class="diff-label">文本：</span>{{ diffNodes[diffCurrentIdx].serverText || '(空)' }}</div>
              <div class="diff-field"><span class="diff-label">类型：</span>{{ ntLabelFn(diffNodes[diffCurrentIdx].serverType) }}</div>
              <div v-if="diffNodes[diffCurrentIdx].serverPropsStr" class="diff-field"><span class="diff-label">属性：</span>{{ diffNodes[diffCurrentIdx].serverPropsStr }}</div>
            </div>
          </div>
        </div>
        <div style="display:flex;justify-content:space-between;margin-top:16px">
          <a-button :disabled="diffCurrentIdx <= 0" @click="diffCurrentIdx--">上一个</a-button>
          <a-button v-if="diffCurrentIdx < diffNodes.length - 1" type="primary" @click="diffCurrentIdx++">下一个</a-button>
          <a-button v-else type="primary" :disabled="!allDiffChosen" :loading="saving" @click="applyDiffMerge">完成合并并上传</a-button>
        </div>
      </template>
    </a-modal>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  ArrowLeftOutlined, SaveOutlined, HistoryOutlined, SearchOutlined,
  ToolOutlined, CommentOutlined, CheckCircleOutlined,
  PlusOutlined, PlusSquareOutlined, DeleteOutlined, CloseOutlined,
  CopyOutlined, SnippetsOutlined,
  NodeExpandOutlined, NodeCollapseOutlined,
  DownloadOutlined, UploadOutlined,
} from '@ant-design/icons-vue';
import { SvgMindMapEngine as MindMap } from '../composables/svgMindMap';
import { caseSetApi, mindNodeApi, commentApi, caseHistoryApi, userApi, customAttributeApi, reviewApi } from '../api';
import { useAppStore } from '../stores/app';
import type { CaseSet, MindNodeData, CaseHistory, User, CustomAttribute } from '../types';
import { NODE_TYPE_LABEL, addAllCustomLabels, setupMouseOverrides } from '../composables/useMindMap';
import { useGuard } from '../composables/useGuard';
import { deleteLocalDraft } from '../composables/useLocalMindMap';
import CommentPanel from '../components/CommentPanel.vue';

MindMap.usePlugin(null);
MindMap.usePlugin(null);
MindMap.usePlugin(null);

const route = useRoute();
const router = useRouter();
const store = useAppStore();
const caseSetId = String(route.params.caseSetId);

const mindMapContainer = ref<HTMLDivElement | null>(null);
let mindMapInstance: any = null;

// === 基础状态 ===
const caseSet = ref<CaseSet | null>(null);
const caseCount = ref(0);
const saving = ref(false);
const mindMapLoading = ref(true);
const actionLock = ref(false);
const { locks, run } = useGuard();
const importFileInput = ref<HTMLInputElement | null>(null);

// === 缩放 & 滚动条 ===
const zoomLevel = ref(100);
const scrollbarH = reactive({ left: 0, width: 100 });
const scrollbarV = reactive({ top: 0, height: 100 });
const hScrollRef = ref<HTMLDivElement | null>(null);
const vScrollRef = ref<HTMLDivElement | null>(null);

// === 右侧面板 ===
const rightPanelOpen = ref(false);
const panelTab = ref<'props' | 'validate' | 'comments'>('props');

// === 编辑表单 (缓冲, 点确定才生效) ===
const editForm = ref<{ text: string; nodeType: string | null; properties: Record<string, any> } | null>(null);
const activeNodeInstance = ref<any>(null);

// === 搜索 ===
const showSearch = ref(false);
const searchText = ref('');
const replaceText = ref('');
const searchMatches = ref<any[]>([]);
const searchIdx = ref(-1);

// === 规范检查 ===
const valErrors = ref<{ uid: string; message: string }[]>([]);
const valIdx = ref(-1);
const valChecked = ref(false);

// === 评论面板 ===
const commentPanelRef = ref<InstanceType<typeof CommentPanel> | null>(null);

// === 历史 ===
const showHistory = ref(false);
const versions = ref<CaseHistory[]>([]);

// === 用例集名称内联编辑 ===
const editingCaseSetName = ref(false);
const caseSetNameInput = ref('');

function startEditCaseSetName() {
  caseSetNameInput.value = caseSet.value?.name || '';
  editingCaseSetName.value = true;
}
async function saveCaseSetName() {
  editingCaseSetName.value = false;
  const newName = caseSetNameInput.value.trim();
  if (!newName || newName === caseSet.value?.name) return;
  await guardAction(async () => {
    try {
      await caseSetApi.rename(caseSetId, newName);
      if (caseSet.value) caseSet.value.name = newName;
      message.success('名称已更新');
    } catch { message.error('更新失败'); }
  });
}

// === 用户/属性/评审 ===
const users = ref<User[]>([]);
const projectAttributes = ref<CustomAttribute[]>([]);
const showReviewModal = ref(false);
const selectedReviewers = ref<string[]>([]);

let historyTimer: ReturnType<typeof setInterval> | null = null;
let cleanupMouseOverrides: (() => void) | null = null;
let dataChangeTimer: ReturnType<typeof setTimeout> | null = null;

/** 通用防重操作包装：执行期间 actionLock=true，防止连点 */
async function guardAction(fn: () => Promise<void>) {
  if (actionLock.value) return;
  actionLock.value = true;
  try { await fn(); } finally { actionLock.value = false; }
}

/** 命令防抖：300ms 内重复点击同一命令只执行最后一次 */
let cmdDebounceTimer: ReturnType<typeof setTimeout> | null = null;
function debouncedExecCmd(cmd: string) {
  if (cmdDebounceTimer) clearTimeout(cmdDebounceTimer);
  cmdDebounceTimer = setTimeout(() => { if (mindMapInstance) mindMapInstance.execCommand(cmd); cmdDebounceTimer = null; }, 120);
}
const dataVersion = ref(0);
const showConflictDialog = ref(false);
const conflictServerTree = ref<MindNodeData[]>([]);
const conflictLocalTree = ref<MindNodeData[]>([]);
const conflictServerVersion = ref(0);


function onKeyboardCopyPaste(e: KeyboardEvent) {
  if (!e.ctrlKey) return;
  const t = e.target as HTMLElement;
  if (t instanceof HTMLInputElement || t instanceof HTMLTextAreaElement || t.isContentEditable) return;
  if (e.key === 'c') { e.preventDefault(); e.stopPropagation(); copySelectedNodes(); }
  if (e.key === 'v') { e.preventDefault(); e.stopPropagation(); pasteNodes(); }
}

// === 状态计算 ===
const statusLabel = computed(() => {
  const s = caseSet.value?.status;
  const m: Record<string, string> = { WRITING: '编写中', PENDING_REVIEW: '待评审', NO_REVIEW: '无需评审', APPROVED: '审核通过' };
  return m[s || ''] || s || '';
});
const statusColor = computed(() => {
  const s = caseSet.value?.status;
  const m: Record<string, string> = { WRITING: 'processing', PENDING_REVIEW: 'warning', NO_REVIEW: 'default', APPROVED: 'success' };
  return m[s || ''] || 'default';
});

const nodeTypeOptions = [
  { value: 'TITLE', label: '用例标题' },
  { value: 'PRECONDITION', label: '前置条件' },
  { value: 'STEP', label: '步骤' },
  { value: 'EXPECTED', label: '预期结果' },
];
/** 当前节点可直接设置的属性 */
/** 当前节点可直接设置的属性 */
const filteredEditAttrs = computed(() => {
  if (!editForm.value) return [];
  const nt = editForm.value.nodeType;
  return projectAttributes.value.filter(a => {
    if (a.name === '标记') return false;
    if (!a.nodeTypeLimit) return true;
    if (!nt) return false;
    return a.nodeTypeLimit.split(',').includes(nt);
  });
});

/** 当前节点不能设置、但子孙节点可以设置的属性 */
const descendantAttrs = computed(() => {
  if (!editForm.value || !activeNodeInstance.value) return [];
  const nt = editForm.value.nodeType;
  const directIds = new Set(filteredEditAttrs.value.map(a => a.id));
  return projectAttributes.value.filter(a => {
    if (a.name === '标记') return false;
    if (directIds.has(a.id)) return false;
    if (!a.nodeTypeLimit) return false;
    const allowed = a.nodeTypeLimit.split(',');
    return hasDescendantWithType(activeNodeInstance.value, allowed);
  });
});

/** 检查节点的子孙中是否有指定类型的节点 */
function hasDescendantWithType(node: any, types: string[]): boolean {
  for (const child of (node.children || [])) {
    const r = child.nodeData?.data?._raw;
    if (r?.nodeType && types.includes(r.nodeType)) return true;
    if (hasDescendantWithType(child, types)) return true;
  }
  return false;
}

const descTileValues = reactive<Record<string, any>>({});

function toggleDescSingleTile(attr: CustomAttribute, option: string) {
  const cur = descTileValues[attr.name];
  const val = cur === option ? undefined : option;
  descTileValues[attr.name] = val;
  applyToDescendants(attr, val);
}
function toggleDescMultiTile(attr: CustomAttribute, option: string) {
  const cur = descTileValues[attr.name];
  const arr: string[] = Array.isArray(cur) ? [...cur] : [];
  const idx = arr.indexOf(option);
  if (idx >= 0) arr.splice(idx, 1); else arr.push(option);
  descTileValues[attr.name] = [...arr];
  applyToDescendants(attr, [...arr]);
}

/** 批量为子孙中符合条件的节点设置属性 */
function applyToDescendants(attr: CustomAttribute, value: any) {
  if (!activeNodeInstance.value || !mindMapInstance) return;
  const allowed = attr.nodeTypeLimit ? attr.nodeTypeLimit.split(',') : [];
  let count = 0;
  function walk(node: any) {
    for (const child of (node.children || [])) {
      const r = child.nodeData?.data?._raw;
      if (r?.nodeType && allowed.includes(r.nodeType)) {
        if (!r.properties) r.properties = {};
        r.properties[attr.name] = value;
        count++;
      }
      walk(child);
    }
  }
  walk(activeNodeInstance.value);
  markDirty();
  scheduleRefreshLabels();
  message.success(`已为 ${count} 个子孙节点设置「${attr.name}」`);
}

// 侧边栏使用 absolute 定位覆盖，不改变导图尺寸，不需要 resize

// =============================================
// 右侧面板控制
// =============================================

function openValidation() {
  rightPanelOpen.value = true;
  panelTab.value = 'validate';
  runValidation();
}

function openComments() {
  rightPanelOpen.value = true;
  panelTab.value = 'comments';
  nextTick(() => commentPanelRef.value?.refresh());
}

function openHistory() {
  showHistory.value = true;
  caseHistoryApi.list(caseSetId, 10).then(res => { versions.value = res.data; });
}

function closePanel() {
  rightPanelOpen.value = false;
}

async function handleExportExcel() {
  await run('exportExcel', async () => {
    try {
      const res = await mindNodeApi.exportExcel(caseSetId);
      const blob = new Blob([res as any], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = `${caseSet.value?.name || '用例集'}.xlsx`; a.click();
      URL.revokeObjectURL(url);
      message.success('导出成功');
    } catch { message.error('导出失败，请确保有有效用例'); }
  });
}

function triggerImportExcel() {
  importFileInput.value?.click();
}

async function handleImportExcel(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  input.value = '';
  await run('importExcel', async () => {
    try {
      await mindNodeApi.importExcel(file, caseSetId);
      message.success('导入成功，正在刷新...');
      mindMapLoading.value = true;
      initialLoadDone = false;
      await loadData();
    } catch { message.error('导入失败'); }
  });
}

// =============================================
// 数据格式转换 (不使用 tag, 自定义SVG渲染)
// =============================================

/** 生成前端稳定节点 ID：n_{时间戳}_{随机串} */
function generateNodeId(): string {
  return 'n_' + Date.now() + '_' + Math.random().toString(36).substring(2, 10);
}

/** 将后端 MindNodeData 转为 simple-mind-map 数据结构 */
function nodeToMM(node: MindNodeData): any {
  const nodeId = node.id || generateNodeId();
  return {
    data: {
      text: node.text,
      uid: nodeId,
      _raw: {
        id: nodeId,
        text: node.text,
        nodeType: node.nodeType || null,
        sortOrder: node.sortOrder || 0,
        isRoot: node.isRoot,
        properties: node.properties,
        commentCount: node.commentCount || 0,
      },
    },
    children: (node.children || []).map(c => nodeToMM(c)),
  };
}

/** 将 simple-mind-map 数据转回后端 MindNodeData（折叠节点回退路径） */
function mmToTree(mmNode: any): MindNodeData {
  const raw = mmNode.data?._raw || {};
  let nodeId = raw.id;
  if (!nodeId) {
    nodeId = generateNodeId();
    if (mmNode.data) {
      if (!mmNode.data._raw) mmNode.data._raw = {};
      mmNode.data._raw.id = nodeId;
    }
  }
  return {
    id: nodeId,
    text: mmNode.data?.text ?? raw.text ?? '',
    nodeType: raw.nodeType || null,
    sortOrder: raw.sortOrder || 0,
    isRoot: raw.isRoot,
    properties: raw.properties || undefined,
    children: (mmNode.children || []).map((c: any) => mmToTree(c)),
  };
}

/** 从渲染树提取完整 MindNodeData 树（用于保存） */
function getFullTree(): MindNodeData[] {
  if (!mindMapInstance?.renderer?.root) return [];
  const root = renderNodeToTree(mindMapInstance.renderer.root);
  root.isRoot = 1;
  return [root];
}

/** 将渲染节点递归转为 MindNodeData（保留 _raw 自定义数据） */
function renderNodeToTree(rNode: any): MindNodeData {
  const raw = rNode.nodeData?.data?._raw || {};
  const text = rNode.nodeData?.data?.text || raw.text || '';

  // 确保每个节点都有稳定 ID，写回渲染树避免下次保存丢失
  let nodeId = raw.id;
  if (!nodeId) {
    nodeId = generateNodeId();
    if (rNode.nodeData?.data) {
      if (!rNode.nodeData.data._raw) rNode.nodeData.data._raw = {};
      rNode.nodeData.data._raw.id = nodeId;
    }
  }

  const renderChildren: any[] = rNode.children || [];
  const dataChildren: any[] = rNode.nodeData?.children || [];
  let children: MindNodeData[];
  if (renderChildren.length > 0) {
    children = renderChildren.map((c: any, i: number) => {
      const child = renderNodeToTree(c);
      child.sortOrder = i;
      return child;
    });
  } else {
    children = dataChildren.map((c: any, i: number) => {
      const child = mmToTree(c);
      child.sortOrder = i;
      return child;
    });
  }
  return {
    id: nodeId,
    text,
    nodeType: raw.nodeType || null,
    sortOrder: raw.sortOrder || 0,
    isRoot: raw.isRoot,
    properties: raw.properties ? JSON.parse(JSON.stringify(raw.properties)) : undefined,
    children,
  };
}

/**
 * 统计有效用例数量。一条路径满足以下全部条件才算一条合格用例：
 * 1) 路径至少 5 个节点（根节点 + ≥1 个功能模块 + 用例标题 + 前置条件 + 步骤 + 预期结果）
 * 2) 最后 4 个节点类型依次为 TITLE → PRECONDITION → STEP → EXPECTED
 * 3) 最后 4 个节点之前的所有普通节点（功能模块）不能设置类型
 * 4) EXPECTED 节点的必填属性已填写
 */
function countValid(node: MindNodeData): number {
  const requiredAttrs = projectAttributes.value
    .filter(a => a.required === 1)
    .map(a => ({ name: a.name, nodeTypeLimit: a.nodeTypeLimit }));
  let count = 0;
  function walk(n: MindNodeData, path: MindNodeData[]) {
    const p = [...path, n];
    if (!n.children || !n.children.length) {
      if (p.length >= 5) {
        const len = p.length;
        // 规则2：最后4个节点类型必须为 TITLE → PRECONDITION → STEP → EXPECTED
        if (p[len-4].nodeType === 'TITLE' && p[len-3].nodeType === 'PRECONDITION'
            && p[len-2].nodeType === 'STEP' && p[len-1].nodeType === 'EXPECTED') {
          // 规则3：前面的功能模块节点不能设置类型
          let moduleClean = true;
          for (let k = 0; k < len - 4; k++) {
            if (p[k].nodeType) { moduleClean = false; break; }
          }
          if (!moduleClean) return;
          // 规则4：EXPECTED 节点的必填属性已填写
          const props = p[len-1].properties || {};
          let valid = true;
          for (const attr of requiredAttrs) {
            if (attr.nodeTypeLimit && !attr.nodeTypeLimit.split(',').includes('EXPECTED')) continue;
            const v = props[attr.name];
            if (v == null || v === '' || (Array.isArray(v) && v.length === 0)) { valid = false; break; }
          }
          if (valid) count++;
        }
      }
    } else { for (const c of n.children) walk(c, p); }
  }
  walk(node, []);
  return count;
}

// =============================================
// 自定义节点标签 (SVG foreignObject) → 见 composables/useMindMap.ts
// =============================================

/** 刷新全部节点的类型/属性/评论标签（合并高频调用） */
let refreshScheduled = false;
function scheduleRefreshLabels() {
  if (refreshScheduled) return;
  refreshScheduled = true;
  requestAnimationFrame(() => {
    if (mindMapInstance?._isSvgEngine) mindMapInstance.refresh();
    else addAllCustomLabels(mindMapInstance);
    refreshScheduled = false;
  });
}
function refreshLabels() {
  if (mindMapInstance?._isSvgEngine) mindMapInstance.refresh();
  else addAllCustomLabels(mindMapInstance);
}

// =============================================
// 节点导航
// =============================================

/** 递归按 uid 查找渲染节点 */
function findNodeByUid(node: any, uid: string): any {
  if (!node) return null;
  const nodeUid = node.nodeData?.data?.uid || node.data?.uid;
  if (nodeUid === uid) return node;
  const children = node.children || node._children || [];
  for (const child of children) {
    const found = findNodeByUid(child, uid);
    if (found) return found;
  }
  return null;
}

/** 定位并激活指定 uid 的节点 */
function navigateToNode(uid: string) {
  if (!mindMapInstance) return;
  const root = mindMapInstance.renderer.root;
  const target = findNodeByUid(root, uid);
  if (target) {
    target.active();
    mindMapInstance.renderer.moveNodeToCenter(target);
  }
}

// =============================================
// 实时同步: 属性面板 → 节点
// =============================================

/** 单选平铺标签的切换（点击已选中的可取消） */
function toggleSingleTileTag(attrName: string, option: string) {
  if (!editForm.value) return;
  const cur = editForm.value.properties[attrName];
  editForm.value.properties[attrName] = cur === option ? undefined : option;
  syncToNode();
}

/** 多选平铺标签的切换 */
function toggleTileTag(attrName: string, option: string) {
  if (!editForm.value) return;
  const cur = editForm.value.properties[attrName];
  const arr: string[] = Array.isArray(cur) ? [...cur] : [];
  const idx = arr.indexOf(option);
  if (idx >= 0) arr.splice(idx, 1); else arr.push(option);
  editForm.value.properties[attrName] = arr;
  syncToNode();
}

/** 将属性面板表单数据同步到当前激活节点 */
function syncToNode(checkAutoChain = false) {
  if (!editForm.value || !activeNodeInstance.value || !mindMapInstance) return;
  const form = editForm.value;
  const node = activeNodeInstance.value;
  if (!node.nodeData?.data) return;

  // 先把完整的 _raw 快照下来，防止 SET_NODE_TEXT 破坏
  const prevRaw = node.nodeData.data._raw
    ? JSON.parse(JSON.stringify(node.nodeData.data._raw))
    : { text: '', nodeType: null, properties: {} };

  // 同步文本
  if (prevRaw.text !== form.text) {
    mindMapInstance.execCommand('SET_NODE_TEXT', node, form.text);
  }

  // SET_NODE_TEXT 之后, 确保 _raw 仍然存在（可能被重建的 data 对象覆盖）
  if (!node.nodeData.data._raw) {
    node.nodeData.data._raw = prevRaw;
  }

  // 节点类型变化 → 清空不再适用的动态属性
  if (checkAutoChain && form.nodeType !== prevRaw.nodeType) {
    const newNt = form.nodeType;
    for (const attr of projectAttributes.value) {
      if (attr.name === '标记') continue;
      if (attr.nodeTypeLimit) {
        const allowed = attr.nodeTypeLimit.split(',');
        if (!newNt || !allowed.includes(newNt)) {
          delete form.properties[attr.name];
        }
      }
    }
  }

  // 构建干净的 properties
  const cleanProps: Record<string, any> = {};
  for (const [k, v] of Object.entries(form.properties)) {
    if (v !== undefined && v !== null) cleanProps[k] = v;
  }

  // 写入 _raw 全部字段
  const raw = node.nodeData.data._raw;
  raw.id = prevRaw.id;
  raw.text = form.text;
  raw.nodeType = form.nodeType;
  raw.sortOrder = prevRaw.sortOrder;
  raw.isRoot = prevRaw.isRoot;
  raw.properties = cleanProps;

  if (checkAutoChain && form.nodeType === 'TITLE') {
    autoAssignCaseChain(node);
  }

  markDirty();
  scheduleRefreshLabels();
}

/**
 * 设置节点为 TITLE 后，自动级联赋值子节点类型（强制覆盖已有类型）：
 * - 第1层子节点 → PRECONDITION
 * - 第2层子节点 → STEP
 * - 第3层子节点 → EXPECTED
 * 如果某层没有子节点则停止
 */
function autoAssignCaseChain(titleNode: any) {
  const typeChain = ['PRECONDITION', 'STEP', 'EXPECTED'];

  function assignLevel(nodes: any[], depth: number) {
    if (depth >= typeChain.length || !nodes || !nodes.length) return;
    for (const node of nodes) {
      if (!node.nodeData) node.nodeData = {};
      if (!node.nodeData.data) node.nodeData.data = {};
      let raw = node.nodeData.data._raw;
      if (!raw) {
        raw = { text: node.nodeData.data.text || '', nodeType: null, properties: {} };
        node.nodeData.data._raw = raw;
      }
      raw.nodeType = typeChain[depth];
      if (node.children?.length) {
        assignLevel(node.children, depth + 1);
      }
    }
  }

  if (titleNode.children?.length) {
    assignLevel(titleNode.children, 0);
  }
  markDirty();
  scheduleRefreshLabels();
}

// =============================================
// 初始化思维导图
// =============================================

// === 缩放控制 ===
function zoomIn() { mindMapInstance?.view?.enlarge(); }
function zoomOut() { mindMapInstance?.view?.narrow(); }
function zoomReset() { mindMapInstance?.view?.reset(); }

/** 初始化 simple-mind-map 实例并绑定事件 */
function initMindMap(initialData?: any) {
  if (!mindMapContainer.value) return;

  mindMapInstance = new MindMap({
    el: mindMapContainer.value,
    data: initialData || { data: { text: '加载中...' }, children: [] },
    theme: 'classic4',
    layout: 'logicalStructure',
    mousewheelAction: 'move',
    textAutoWrapWidth: 300,
    enableFreeDrag: false,
    initRootNodePosition: [10, 'center'],
    enableShortcutOnlyWhenMouseInSvg: true,
    useLeftKeySelectionRightKeyDrag: true,
    enableNodeTransitionMove: false,
    createNodePrefixContent: false,
    isShowExpandNum: false,
    //openPerformance: true,
    performanceConfig: { time: 250, padding: 200, removeNodeWhenOutCanvas: true },
    themeConfig: {
      second: { marginX: 60, marginY: 40 },
      node: { marginX: 40, marginY: 30 },
    },
  });

  // 节点选中 → 仅单选时打开属性面板，多选(框选)不弹
  mindMapInstance.on('node_active', (_: any, nodes: any[]) => {
    // 切换节点时重置子孙属性设置的选中状态
    Object.keys(descTileValues).forEach(k => delete descTileValues[k]);
    if (nodes.length === 1) {
      const node = nodes[0];
      activeNodeInstance.value = node;
      const raw = node.nodeData?.data?._raw || node.data?._raw;
      const currentText = raw?.text ?? node.nodeData?.data?.text ?? node.data?.text ?? '';
      editForm.value = {
        text: currentText,
        nodeType: raw?.nodeType || null,
        properties: raw?.properties ? JSON.parse(JSON.stringify(raw.properties)) : {},
      };
      if (!rightPanelOpen.value) {
        rightPanelOpen.value = true;
        panelTab.value = 'props';
      }
      if (panelTab.value === 'comments') {
        nextTick(() => commentPanelRef.value?.refresh());
      }
    } else if (nodes.length > 1) {
      activeNodeInstance.value = null;
      editForm.value = null;
    } else {
      activeNodeInstance.value = null;
      editForm.value = null;
      if (panelTab.value === 'props') {
        rightPanelOpen.value = false;
      }
    }
  });

  // 行内编辑完成 → 同步 _raw
  mindMapInstance.on('node_text_edit_end', (node: any, text: string) => {
    const raw = node.nodeData?.data?._raw || node.data?._raw;
    if (raw) raw.text = text;
    if (editForm.value && activeNodeInstance.value === node) {
      editForm.value.text = text;
    }
  });

  // 数据变化 → debounce 更新计数 + 标记脏
  mindMapInstance.on('data_change', () => {
    if (!initialLoadDone) return;
    if (dataChangeTimer) clearTimeout(dataChangeTimer);
    dataChangeTimer = window.setTimeout(() => {
      const tree = getFullTree();
      if (tree.length > 0) caseCount.value = countValid(tree[0]);
      markDirty();
      dataChangeTimer = null;
    }, 300);
  });

  // 渲染完成 → 注入自定义标签 + 首次渲染后启用脏检测
  mindMapInstance.on('node_tree_render_end', () => {
    scheduleRefreshLabels();
    if (!initialLoadDone) {
      console.log(`[perf] first render complete at +${(performance.now() - perfMountStart).toFixed(0)}ms`);
      mindMapLoading.value = false;
      // 延迟启用脏检测：让 simple-mind-map 内部后续事件（scrollbar/layout微调）先结束
      // 这样即使节点少、渲染同步完成，也不会误触 markDirty
      setTimeout(() => {
        if (dataChangeTimer) { clearTimeout(dataChangeTimer); dataChangeTimer = null; }
        initialLoadDone = true;
        markClean();
      }, 500);
    }
  });

  // 缩放变化 → 更新百分比显示
  mindMapInstance.on('scale', (scale: number) => {
    zoomLevel.value = Math.round(scale * 100);
  });

  // 滚动条位置变化
  mindMapInstance.on('scrollbar_change', (data: any) => {
    scrollbarH.left = data.horizontal.left;
    scrollbarH.width = data.horizontal.width;
    scrollbarV.top = data.vertical.top;
    scrollbarV.height = data.vertical.height;
  });

  // 告知 Scrollbar 插件容器尺寸
  nextTick(() => {
    if (mindMapContainer.value) {
      const rect = mindMapContainer.value.getBoundingClientRect();
      mindMapInstance.scrollbar?.setScrollBarWrapSize(rect.width, rect.height);
    }
  });
}

function execCmd(cmd: string) {
  if (mindMapInstance) mindMapInstance.execCommand(cmd);
}

// =============================================
// 节点复制/粘贴（含子树）
// =============================================

interface ClipNode {
  data: { text: string; uid?: string; _raw?: any };
  children: ClipNode[];
}
let nodeClipboard: ClipNode[] = [];

/** 递归提取节点数据用于复制（清除 id，保存时重新分配） */
function extractClipNode(renderNode: any): ClipNode {
  const d = renderNode.nodeData?.data || {};
  return {
    data: {
      text: d.text || '节点',
      // 不保留 uid，由库重新生成；清除 id，保存时重新分配
      _raw: d._raw ? { ...d._raw, id: undefined } : { text: d.text || '节点' },
    },
    children: (renderNode.children || []).map(extractClipNode),
  };
}

/** 复制选中节点到剪贴板（只取根节点，过滤子节点避免重复） */
function copySelectedNodes() {
  const selected: any[] = mindMapInstance?.renderer?.activeNodeList || [];
  if (!selected.length) { message.warning('请先选中节点'); return; }
  // 只保留"根"节点：过滤掉父节点也在选中列表中的节点
  // 否则 A→B→C 链式选中会产生3份重复子树
  const selectedSet = new Set(selected);
  const roots = selected.filter((n: any) => !selectedSet.has(n.parent));
  nodeClipboard = roots.map(extractClipNode);
  message.success(`已复制 ${nodeClipboard.length} 个节点`);
}

/** 为粘贴的子树中每个节点生成全新唯一ID，防止多次粘贴产生重复 */
function assignFreshIds(node: ClipNode) {
  const freshId = generateNodeId();
  if (node.data._raw) node.data._raw.id = freshId;
  node.data.uid = freshId;
  node.children.forEach(assignFreshIds);
}

/** 将剪贴板节点粘贴为当前节点的子节点（每次深拷贝+全新ID保证独立） */
function pasteNodes() {
  if (!nodeClipboard.length) { message.warning('剪贴板为空'); return; }
  const target = activeNodeInstance.value;
  if (!target) { message.warning('请先选中目标节点'); return; }
  const copies: ClipNode[] = JSON.parse(JSON.stringify(nodeClipboard));
  copies.forEach(assignFreshIds);
  for (const clip of copies) {
    mindMapInstance.execCommand('INSERT_CHILD_NODE', false, [target], clip.data, clip.children);
  }
  markDirty();
  scheduleRefreshLabels();
  message.success(`已粘贴 ${copies.length} 个节点`);
}

// =============================================
// 数据加载
// =============================================

async function loadData() {
  mindMapLoading.value = true;
  const t0 = performance.now();
  const [csRes, treeRes] = await Promise.all([caseSetApi.get(caseSetId), mindNodeApi.tree(caseSetId)]);
  const t1 = performance.now();

  caseSet.value = csRes.data;
  const serverData = treeRes.data as any;
  const serverTree: MindNodeData[] = serverData.tree || [];
  const serverVer: number = serverData.version || 0;
  dataVersion.value = serverVer;

  await deleteLocalDraft(caseSetId).catch(() => {});

  const t2 = performance.now();
  const mmData = serverTree.length
    ? nodeToMM(serverTree[0])
    : { data: { text: caseSet.value?.name || '新用例集' }, children: [] };
  const t3 = performance.now();

  initialLoadDone = false;
  if (mindMapInstance) mindMapInstance.setData(mmData);
  const t4 = performance.now();

  caseCount.value = serverTree.length > 0 ? countValid(serverTree[0]) : 0;
  const t5 = performance.now();

  const jsonSize = JSON.stringify(serverData).length;
  console.log(
    `[perf] loadData | API=${(t1-t0).toFixed(0)}ms nodeToMM=${(t3-t2).toFixed(0)}ms ` +
    `setData=${(t4-t3).toFixed(0)}ms countValid=${(t5-t4).toFixed(0)}ms ` +
    `total=${(t5-t0).toFixed(0)}ms | nodes=${serverTree.length ? countNodesMM(serverTree[0]) : 0} jsonSize=${(jsonSize/1024).toFixed(1)}KB`
  );
}

function countNodesMM(node: MindNodeData): number {
  let c = 1;
  if (node.children) for (const ch of node.children) c += countNodesMM(ch);
  return c;
}

// =============================================
// 保存
// =============================================

async function handleSave() {
  if (saving.value) return;
  saving.value = true;
  const st0 = performance.now();
  try {
    const tree = getFullTree();
    const st1 = performance.now();
    const res = await mindNodeApi.batchSave(caseSetId, tree, dataVersion.value);
    const st2 = performance.now();
    console.log(`[perf] save | getFullTree=${(st1-st0).toFixed(0)}ms API=${(st2-st1).toFixed(0)}ms`);
    const resData = res.data as any;

    if (resData?.conflict) {
      conflictServerTree.value = resData.serverTree;
      conflictLocalTree.value = tree;
      conflictServerVersion.value = resData.serverVersion;
      showConflictDialog.value = true;
      saving.value = false;
      return;
    }

    dataVersion.value = resData.version;
    caseCount.value = resData.caseCount ?? (tree.length > 0 ? countValid(tree[0]) : 0);
    await caseHistoryApi.save(caseSetId);
    await deleteLocalDraft(caseSetId);
    markClean();
    if (resData.deletedComments > 0) {
      message.warning(`已同步到云端，${resData.deletedComments} 条关联评论已随删除的节点一并清理`);
    } else {
      message.success('已同步到云端');
    }
  } catch { /* handled */ } finally { saving.value = false; }
}

// =============================================
// 查找替换 (逐个定位到节点)
// =============================================

function toggleSearch() {
  showSearch.value = !showSearch.value;
  if (!showSearch.value) { searchMatches.value = []; searchIdx.value = -1; }
}

function collectMatchNodes(): any[] {
  if (!mindMapInstance || !searchText.value) return [];
  const matches: any[] = [];
  function walk(node: any) {
    const text = node.nodeData?.data?.text || node.data?.text || '';
    if (text.includes(searchText.value)) matches.push(node);
    (node.children || []).forEach(walk);
  }
  walk(mindMapInstance.renderer.root);
  return matches;
}

function onSearchChange() {
  if (!searchText.value) { searchMatches.value = []; searchIdx.value = -1; return; }
  searchMatches.value = collectMatchNodes();
  searchIdx.value = searchMatches.value.length ? 0 : -1;
  if (searchMatches.value.length) {
    const n = searchMatches.value[0];
    n.active();
    mindMapInstance.renderer.moveNodeToCenter(n);
  }
}

function findNext() {
  if (!mindMapInstance) return;
  if (!searchMatches.value.length) { onSearchChange(); return; }
  searchIdx.value = (searchIdx.value + 1) % searchMatches.value.length;
  const n = searchMatches.value[searchIdx.value];
  if (n) { n.active(); mindMapInstance.renderer?.moveNodeToCenter(n); }
}

function findPrev() {
  if (!mindMapInstance || !searchMatches.value.length) return;
  searchIdx.value = (searchIdx.value - 1 + searchMatches.value.length) % searchMatches.value.length;
  const n = searchMatches.value[searchIdx.value];
  if (n) { n.active(); mindMapInstance.renderer?.moveNodeToCenter(n); }
}

function replaceOneFn() {
  if (!mindMapInstance || searchIdx.value < 0 || !searchMatches.value.length) return;
  const node = searchMatches.value[searchIdx.value];
  if (!node) return;
  const oldText = node.nodeData?.data?.text || '';
  const newText = oldText.replace(searchText.value, replaceText.value);
  mindMapInstance.execCommand('SET_NODE_TEXT', node, newText);
  const raw = node.nodeData?.data?._raw || node.data?._raw;
  if (raw) raw.text = newText;
  searchMatches.value = collectMatchNodes();
  if (searchIdx.value >= searchMatches.value.length) searchIdx.value = Math.max(0, searchMatches.value.length - 1);
  message.success('已替换');
}

function replaceAllFn() {
  if (!searchText.value || !mindMapInstance) return;
  const data = mindMapInstance.getData();
  function walk(n: any) {
    if (n.data?.text) n.data.text = n.data.text.replaceAll(searchText.value, replaceText.value);
    if (n.data?._raw?.text) n.data._raw.text = n.data._raw.text.replaceAll(searchText.value, replaceText.value);
    if (n.children) n.children.forEach(walk);
  }
  walk(data);
  mindMapInstance.setData(data);
  searchMatches.value = [];
  searchIdx.value = -1;
  message.success('全部替换完成');
}

// =============================================
// 规范检查 (客户端验证, 定位到分支末尾节点)
// =============================================

/** 校验思维导图的结构和必填属性 */
function runValidation() {
  valErrors.value = [];
  valIdx.value = -1;
  valChecked.value = true;

  if (!mindMapInstance?.renderer?.root) return;
  const root = mindMapInstance.renderer.root;
  const errors: { uid: string; message: string }[] = [];

  function walk(node: any, path: any[]) {
    const currentPath = [...path, node];
    const children = node.children || [];

    if (children.length === 0) {
      const leafUid = node.nodeData?.data?.uid || '';
      const pathNames = currentPath.map((n: any) => n.nodeData?.data?.text || '?');
      const pathStr = pathNames.join(' → ');

      if (currentPath.length < 5) {
        errors.push({ uid: leafUid, message: `路径层数不足(${currentPath.length}层,至少5层): ${pathStr}` });
        return;
      }

      const len = currentPath.length;
      const getType = (n: any) => n.nodeData?.data?._raw?.nodeType;
      const expected = ['TITLE', 'PRECONDITION', 'STEP', 'EXPECTED'] as const;
      const actual = [getType(currentPath[len-4]), getType(currentPath[len-3]), getType(currentPath[len-2]), getType(currentPath[len-1])];

      const issues: string[] = [];
      for (let i = 0; i < 4; i++) {
        if (actual[i] !== expected[i]) {
          const actualLabel = actual[i] ? NODE_TYPE_LABEL[actual[i]] : '未设置';
          issues.push(`第${len - 4 + i + 1}层应为"${NODE_TYPE_LABEL[expected[i]]}"(当前: ${actualLabel})`);
        }
      }

      // 检查最后4个节点之前是否有节点设置了类型（不允许）
      for (let k = 0; k < len - 4; k++) {
        const earlyType = getType(currentPath[k]);
        if (earlyType) {
          const earlyLabel = NODE_TYPE_LABEL[earlyType] || earlyType;
          issues.push(`第${k + 1}层"${currentPath[k].nodeData?.data?.text || '?'}"不应设置类型(当前: ${earlyLabel})，只有最后4个节点可以有类型`);
        }
      }

      if (issues.length) {
        errors.push({ uid: leafUid, message: `${pathStr}\n${issues.join('; ')}` });
      } else {
        // 检查必填属性
        const caseTypes = ['TITLE', 'PRECONDITION', 'STEP', 'EXPECTED'];
        for (let i = 0; i < 4; i++) {
          const nd = currentPath[len - 4 + i];
          const ndProps = nd.nodeData?.data?._raw?.properties || {};
          for (const attr of projectAttributes.value) {
            if (!attr.required) continue;
            if (attr.nodeTypeLimit && !attr.nodeTypeLimit.split(',').includes(caseTypes[i])) continue;
            const val = ndProps[attr.name];
            if (val === undefined || val === null || val === '' || (Array.isArray(val) && val.length === 0)) {
              errors.push({ uid: leafUid, message: `${pathStr}\n${nd.nodeData?.data?.text || '?'}: 必填属性"${attr.name}"未填写` });
            }
          }
        }
      }
    } else {
      children.forEach((c: any) => walk(c, currentPath));
    }
  }

  walk(root, []);
  valErrors.value = errors;

  if (errors.length) {
    message.warning(`共${errors.length}条不符合规范`);
  } else {
    message.success('所有分支符合规范');
  }
}

function navigateToError(idx: number) {
  valIdx.value = idx;
  const err = valErrors.value[idx];
  if (err?.uid) navigateToNode(err.uid);
}

// =============================================
// 状态/评审
// =============================================

async function handleStatusChange(status: string) {
  await guardAction(async () => {
    if (status === 'PENDING_REVIEW') {
      try {
        const valRes = await caseSetApi.validate(caseSetId);
        const valData = valRes.data as any;
        if (!valData.valid) {
          message.error(`用例集不符合规范，共 ${valData.errorCount} 条错误，请先修正`);
          return;
        }
      } catch { message.error('验证失败'); return; }
      try { users.value = (await userApi.listAll()).data; } catch { /* */ }
      try {
        const revRes = await reviewApi.list(caseSetId);
        selectedReviewers.value = (revRes.data || []).map((r: any) => r.reviewerId);
      } catch { selectedReviewers.value = []; }
      showReviewModal.value = true;
      return;
    }
    await caseSetApi.updateStatus(caseSetId, status);
    if (caseSet.value) caseSet.value.status = status;
    message.success('状态已更新');
  });
}

const submittingReview = ref(false);
async function submitReview() {
  if (!selectedReviewers.value.length) { message.error('请选择评审人'); return; }
  if (submittingReview.value) return;
  submittingReview.value = true;
  try {
    await caseSetApi.updateStatus(caseSetId, 'PENDING_REVIEW', selectedReviewers.value);
    if (caseSet.value) caseSet.value.status = 'PENDING_REVIEW';
    showReviewModal.value = false;
    message.success('已提交评审');
  } catch { /* handled */ } finally { submittingReview.value = false; }
}

// =============================================
// 评论角标刷新（由 CommentPanel 触发）
// =============================================

function onCommentCountChanged(nodeId: string, count: number) {
  if (!mindMapInstance?.renderer?.root) return;
  (function walkUpdate(n: any) {
    const r = n.nodeData?.data?._raw;
    if (r && r.id === nodeId) r.commentCount = count;
    (n.children || []).forEach(walkUpdate);
  })(mindMapInstance.renderer.root);
  scheduleRefreshLabels();
}

// =============================================
// 历史版本
// =============================================

const restoringVersion = ref(false);
async function restoreVersion(id: string) {
  if (restoringVersion.value) return;
  restoringVersion.value = true;
  try {
    await caseHistoryApi.restore(id);
    message.success('版本已恢复');
    loadData();
    showHistory.value = false;
  } catch { message.error('恢复失败'); } finally { restoringVersion.value = false; }
}

// =============================================
// 冲突解决
// =============================================

function flattenNodes(tree: MindNodeData[], prefix = ''): Map<string, MindNodeData> {
  const map = new Map<string, MindNodeData>();
  function walk(nodes: MindNodeData[], pathPrefix: string) {
    for (const node of nodes) {
      const key = node.id || pathPrefix + '/' + node.text;
      map.set(key, node);
      if (node.children?.length) walk(node.children, key);
    }
  }
  walk(tree, prefix);
  return map;
}

async function resolveConflictUseLocal() {
  if (saving.value) return;
  saving.value = true;
  try {
    const tree = conflictLocalTree.value;
    const res = await mindNodeApi.batchSave(caseSetId, tree);
    const resData = res.data as any;
    if (resData?.conflict) {
      message.error('仍有冲突，请刷新页面后重试');
      saving.value = false;
      return;
    }
    dataVersion.value = resData.version;
    caseCount.value = resData.caseCount ?? 0;
    await deleteLocalDraft(caseSetId);
    markClean();
    showConflictDialog.value = false;
    message.success('已使用本地版本覆盖云端');
    navigateAfterSave();
  } catch { /* */ } finally { saving.value = false; }
}

async function resolveConflictUseServer() {
  dataVersion.value = conflictServerVersion.value;
  const serverTree = conflictServerTree.value;
  mindMapLoading.value = true;
  initialLoadDone = false;
  if (serverTree.length) {
    const mmData = nodeToMM(serverTree[0]);
    if (mindMapInstance) mindMapInstance.setData(mmData);
    caseCount.value = countValid(serverTree[0]);
  }
  await deleteLocalDraft(caseSetId);
  showConflictDialog.value = false;
  message.success('已使用云端版本');
  navigateAfterSave();
}

// =============================================
// 逐节点差异对比合并
// =============================================

interface DiffNode {
  nodeId: string;
  localText: string; localType: string | null; localProps: Record<string, any>;
  serverText: string; serverType: string | null; serverProps: Record<string, any>;
  localPropsStr: string; serverPropsStr: string;
  choice: 'local' | 'server' | null;
}
const showNodeDiffDialog = ref(false);
const diffNodes = ref<DiffNode[]>([]);
const diffCurrentIdx = ref(0);
const allDiffChosen = computed(() => diffNodes.value.every(d => d.choice !== null));

function ntLabelFn(t: string | null) {
  if (!t) return '(未设置)';
  return ({ TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果' } as any)[t] || t;
}

function flattenTreeNodes(tree: MindNodeData[]): Map<string, MindNodeData> {
  const map = new Map<string, MindNodeData>();
  function walk(nodes: MindNodeData[]) {
    for (const n of nodes) {
      if (n.id) map.set(n.id, n);
      if (n.children?.length) walk(n.children);
    }
  }
  walk(tree);
  return map;
}

function propsToStr(props: Record<string, any> | undefined): string {
  if (!props || !Object.keys(props).length) return '';
  return Object.entries(props).map(([k, v]) => `${k}: ${JSON.stringify(v)}`).join('; ');
}

function openNodeDiff() {
  const localMap = flattenTreeNodes(conflictLocalTree.value);
  const serverMap = flattenTreeNodes(conflictServerTree.value);
  const allIds = new Set([...localMap.keys(), ...serverMap.keys()]);
  const diffs: DiffNode[] = [];

  for (const nid of allIds) {
    const local = localMap.get(nid);
    const server = serverMap.get(nid);
    const lt = local?.text ?? '';
    const st = server?.text ?? '';
    const lnt = local?.nodeType ?? null;
    const snt = server?.nodeType ?? null;
    const lp = local?.properties ?? {};
    const sp = server?.properties ?? {};
    const lps = propsToStr(lp);
    const sps = propsToStr(sp);

    const onlyLocal = local && !server;
    const onlyServer = !local && server;
    const contentDiff = lt !== st || lnt !== snt || lps !== sps;

    if (onlyLocal || onlyServer || contentDiff) {
      diffs.push({
        nodeId: nid,
        localText: lt, localType: lnt, localProps: lp, localPropsStr: lps,
        serverText: st, serverType: snt, serverProps: sp, serverPropsStr: sps,
        choice: onlyLocal ? 'local' : onlyServer ? 'server' : null,
      });
    }
  }

  diffNodes.value = diffs;
  diffCurrentIdx.value = 0;
  showConflictDialog.value = false;
  showNodeDiffDialog.value = true;
}

function diffChooseAllLocal() {
  for (const d of diffNodes.value) d.choice = 'local';
}
function diffChooseAllServer() {
  for (const d of diffNodes.value) d.choice = 'server';
}

function buildMergedTree(
  localNodes: MindNodeData[],
  serverNodes: MindNodeData[],
  choiceMap: Map<string, 'local' | 'server' | null>,
  localMap: Map<string, MindNodeData>,
  serverMap: Map<string, MindNodeData>,
): MindNodeData[] {
  const localIds = localNodes.map(n => n.id!);
  const serverIds = serverNodes.map(n => n.id!);
  const localIdSet = new Set(localIds);
  const serverIdSet = new Set(serverIds);

  const result: MindNodeData[] = [];
  const processed = new Set<string>();

  for (const lc of localNodes) {
    const nid = lc.id!;
    processed.add(nid);
    if (serverIdSet.has(nid)) {
      const sc = serverMap.get(nid)!;
      const choice = choiceMap.get(nid);
      const merged = JSON.parse(JSON.stringify(choice === 'server' ? sc : lc)) as MindNodeData;
      merged.children = buildMergedTree(lc.children || [], sc.children || [], choiceMap, localMap, serverMap);
      result.push(merged);
    } else {
      const choice = choiceMap.get(nid);
      if (choice !== 'server') {
        result.push(JSON.parse(JSON.stringify(lc)));
      }
    }
  }

  for (const sc of serverNodes) {
    const nid = sc.id!;
    if (!processed.has(nid)) {
      const choice = choiceMap.get(nid);
      if (choice !== 'local') {
        result.push(JSON.parse(JSON.stringify(sc)));
      }
    }
  }

  return result;
}

async function applyDiffMerge() {
  const choiceMap = new Map<string, 'local' | 'server' | null>();
  for (const d of diffNodes.value) choiceMap.set(d.nodeId, d.choice);
  const localMap = flattenTreeNodes(conflictLocalTree.value);
  const serverMap = flattenTreeNodes(conflictServerTree.value);
  const mergedTree = buildMergedTree(conflictLocalTree.value, conflictServerTree.value, choiceMap, localMap, serverMap);

  saving.value = true;
  try {
    const res = await mindNodeApi.batchSave(caseSetId, mergedTree);
    const resData = res.data as any;
    if (resData?.conflict) {
      message.error('仍有冲突，请刷新后重试');
      saving.value = false;
      return;
    }
    dataVersion.value = resData.version;
    caseCount.value = resData.caseCount ?? 0;

    if (mergedTree.length) {
      mindMapLoading.value = true;
      initialLoadDone = false;
      const mmData = nodeToMM(mergedTree[0]);
      if (mindMapInstance) mindMapInstance.setData(mmData);
    }
    await deleteLocalDraft(caseSetId);
    showNodeDiffDialog.value = false;
    message.success('合并完成，已同步到云端');
    navigateAfterSave();
  } catch { /* */ } finally { saving.value = false; }
}

// =============================================
// 评论tab自动加载 & 退出保存提示
// =============================================

watch(panelTab, (tab) => {
  if (tab === 'comments') nextTick(() => commentPanelRef.value?.refresh());
});

const hasUnsavedChanges = ref(false);
let initialLoadDone = false;
let changeCounter = 0;
let savedChangeCounter = 0;
let perfMountStart = 0;

function markDirty() {
  if (!initialLoadDone) return;
  changeCounter++;
  hasUnsavedChanges.value = changeCounter !== savedChangeCounter;
}
function markClean() {
  savedChangeCounter = changeCounter;
  hasUnsavedChanges.value = false;
}
function onBeforeUnload(e: BeforeUnloadEvent) {
  if (hasUnsavedChanges.value) { e.preventDefault(); e.returnValue = ''; }
}

const showExitConfirm = ref(false);
const pendingRoute = ref('');

onBeforeRouteLeave((to) => {
  if (!hasUnsavedChanges.value) return true;
  pendingRoute.value = to.fullPath;
  showExitConfirm.value = true;
  return false;
});
function handleExitCancel() { showExitConfirm.value = false; pendingRoute.value = ''; }
function handleExitDiscard() { hasUnsavedChanges.value = false; showExitConfirm.value = false; router.push(pendingRoute.value); }
async function handleExitSave() {
  showExitConfirm.value = false;
  await handleSave();
  if (!showConflictDialog.value && !showNodeDiffDialog.value) {
    hasUnsavedChanges.value = false;
    router.push(pendingRoute.value);
  }
}
function navigateAfterSave() {
  if (pendingRoute.value) {
    hasUnsavedChanges.value = false;
    const target = pendingRoute.value;
    pendingRoute.value = '';
    router.push(target);
  }
}

// =============================================
// 评论导航到节点
// =============================================

/** 根据评论中的 nodeId 定位到对应节点 */
function navigateToCommentNode(nodeId: string) {
  if (!mindMapInstance?.renderer?.root || !nodeId) return;
  function findByRawId(n: any): any {
    const raw = n.nodeData?.data?._raw;
    if (raw?.id === nodeId) return n;
    for (const c of (n.children || [])) { const found = findByRawId(c); if (found) return found; }
    return null;
  }
  const target = findByRawId(mindMapInstance.renderer.root);
  if (target) { target.active(); mindMapInstance.renderer.moveNodeToCenter(target); }
}

// =============================================
// 生命周期
// =============================================

onMounted(async () => {
  const mt0 = performance.now();
  perfMountStart = mt0;

  // 所有网络请求并行发出，消除串行等待
  const [uRes, attrRes, csRes, treeRes] = await Promise.all([
    userApi.listAll(),
    store.currentProject ? customAttributeApi.list(store.currentProject.id) : Promise.resolve({ data: [] }),
    caseSetApi.get(caseSetId),
    mindNodeApi.tree(caseSetId),
  ]);
  const mt1 = performance.now();

  users.value = uRes.data;
  projectAttributes.value = (attrRes as any).data || [];
  caseSet.value = csRes.data;
  const serverData = treeRes.data as any;
  const serverTree: MindNodeData[] = serverData.tree || [];
  dataVersion.value = serverData.version || 0;

  await deleteLocalDraft(caseSetId).catch(() => {});

  const mt2 = performance.now();
  const mmData = serverTree.length
    ? nodeToMM(serverTree[0])
    : { data: { text: caseSet.value?.name || '新用例集' }, children: [] };
  const mt3 = performance.now();

  await nextTick();
  initialLoadDone = false;
  initMindMap(mmData);
  cleanupMouseOverrides = setupMouseOverrides(mindMapContainer.value!, () => mindMapInstance);
  const mt4 = performance.now();

  caseCount.value = serverTree.length > 0 ? countValid(serverTree[0]) : 0;

  const jsonSize = JSON.stringify(serverData).length;
  const nodeCount = serverTree.length > 0 ? countNodesMM(serverTree[0]) : 0;
  console.log(
    `[perf] onMounted | fetch=${(mt1-mt0).toFixed(0)}ms nodeToMM=${(mt3-mt2).toFixed(0)}ms ` +
    `initMindMap=${(mt4-mt3).toFixed(0)}ms total=${(mt4-mt0).toFixed(0)}ms ` +
    `| nodes=${nodeCount} json=${(jsonSize/1024).toFixed(1)}KB`
  );

  historyTimer = setInterval(() => caseHistoryApi.save(caseSetId).catch(() => {}), 15 * 60 * 1000);
  window.addEventListener('beforeunload', onBeforeUnload);
  window.addEventListener('keydown', onKeyboardCopyPaste, true);
  window.addEventListener('resize', onResize);
});

function onResize() {
  if (mindMapContainer.value && mindMapInstance?.scrollbar) {
    const rect = mindMapContainer.value.getBoundingClientRect();
    mindMapInstance.scrollbar.setScrollBarWrapSize(rect.width, rect.height);
  }
}

onUnmounted(() => {
  if (historyTimer) clearInterval(historyTimer);
  if (dataChangeTimer) clearTimeout(dataChangeTimer);
  if (mindMapInstance) { mindMapInstance.destroy(); mindMapInstance = null; }
  cleanupMouseOverrides?.();
  window.removeEventListener('beforeunload', onBeforeUnload);
  window.removeEventListener('keydown', onKeyboardCopyPaste, true);
  window.removeEventListener('resize', onResize);
});
</script>

<style scoped>
.editor-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #f0f0f0; padding: 0 16px;
  height: 48px; line-height: 48px; flex-shrink: 0;
}
.search-bar {
  background: #fff; padding: 8px 16px; border-bottom: 1px solid #f0f0f0;
  display: flex; gap: 8px; align-items: center; flex-shrink: 0;
}

/* 主体布局: 导图全屏, 面板浮动覆盖在右侧 */
.editor-body {
  flex: 1; position: relative; overflow: hidden;
}
.mm-area {
  width: 100%; height: 100%; position: relative; overflow: hidden; background: #fafafa;
}
.right-panel {
  position: absolute; right: 0; top: 0; bottom: 0;
  width: 320px; border-left: 1px solid #f0f0f0;
  background: #fff; display: flex; flex-direction: column; overflow: hidden;
  z-index: 100; box-shadow: -2px 0 8px rgba(0,0,0,0.06);
}
.rp-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 12px; border-bottom: 1px solid #f0f0f0; flex-shrink: 0;
}
.rp-body {
  flex: 1; overflow-y: auto; padding: 12px;
}

.prop-field { margin-bottom: 10px; }
.prop-field label { display: block; font-size: 12px; color: #999; margin-bottom: 3px; }

/* 属性选择器圆角 */
.prop-field :deep(.ant-select-selector) { border-radius: 16px !important; }

/* 平铺布局 */
.tile-group { display: flex; flex-wrap: wrap; gap: 6px; }

/* 平铺标签（单选和多选共用） */
.tile-tag {
  display: inline-flex; align-items: center; justify-content: center;
  padding: 2px 12px; border-radius: 16px; font-size: 12px; cursor: pointer;
  border: 1px solid #d9d9d9; background: #fff; color: #595959;
  transition: all 0.2s; user-select: none; line-height: 22px;
}
.tile-tag:hover { border-color: #1677ff; color: #1677ff; }
.tile-tag.active { background: #1677ff; border-color: #1677ff; color: #fff; }
.tile-tag.desc-tile:hover { border-color: #722ed1; color: #722ed1; }
.tile-tag.desc-tile.active { background: #722ed1; border-color: #722ed1; color: #fff; }
.tile-tag.priority-p0.active { background: #f5222d; border-color: #f5222d; }
.tile-tag.priority-p1.active { background: #fa8c16; border-color: #fa8c16; }
.tile-tag.priority-p2.active { background: #1677ff; border-color: #1677ff; }
.tile-tag.priority-p3.active { background: #8c8c8c; border-color: #8c8c8c; }

.empty-hint {
  display: flex; align-items: center; justify-content: center;
  height: 200px; color: #ccc; font-size: 14px;
}
.mm-loading-overlay {
  position: absolute; inset: 0; z-index: 200;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: rgba(250, 250, 250, 0.85); backdrop-filter: blur(2px);
}
/* 滚动条 */
.mm-scrollbar { position: absolute; z-index: 50; }
.mm-scrollbar-h {
  left: 0; bottom: 0; right: 14px; height: 10px;
  cursor: pointer;
}
.mm-scrollbar-v {
  right: 0; top: 0; bottom: 14px; width: 10px;
  cursor: pointer;
}
.mm-scrollbar-thumb {
  position: absolute; border-radius: 5px;
  background: rgba(0,0,0,0.15); transition: background 0.2s;
}
.mm-scrollbar-thumb:hover, .mm-scrollbar-thumb:active { background: rgba(0,0,0,0.35); }
.mm-scrollbar-h .mm-scrollbar-thumb { height: 100%; min-width: 30px; }
.mm-scrollbar-v .mm-scrollbar-thumb { width: 100%; min-height: 30px; }

/* 缩放控件 */
.mm-zoom-bar {
  position: absolute; right: 16px; bottom: 20px; z-index: 60;
  display: flex; align-items: center; gap: 2px;
  background: #fff; border: 1px solid #e4e7ed; border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08); padding: 2px 4px;
  user-select: none;
}
.mm-zoom-btn {
  width: 28px; height: 28px; border: none; background: transparent;
  font-size: 16px; font-weight: 600; cursor: pointer; border-radius: 4px;
  display: flex; align-items: center; justify-content: center; color: #595959;
}
.mm-zoom-btn:hover { background: #f5f5f5; color: #1677ff; }
.mm-zoom-label {
  font-size: 12px; min-width: 42px; text-align: center;
  cursor: pointer; color: #595959; line-height: 28px;
}
.mm-zoom-label:hover { color: #1677ff; }

.tips-bar {
  position: absolute; bottom: 16px; left: 50%; transform: translateX(-50%);
  background: rgba(0,0,0,0.45); color: #fff; font-size: 11px;
  padding: 4px 14px; border-radius: 4px; pointer-events: none; white-space: nowrap;
}

/* 规范检查错误列表 */
.val-list { max-height: 500px; overflow-y: auto; }
.val-item {
  display: flex; align-items: flex-start; gap: 6px; padding: 8px;
  border-radius: 4px; cursor: pointer; font-size: 12px; line-height: 1.6;
  border: 1px solid transparent; margin-bottom: 4px; transition: all 0.15s;
}
.val-item:hover { background: #fff2f0; }
.val-item.active { background: #fff2f0; border-color: #ffccc7; }
.val-idx {
  background: #ff4d4f; color: #fff; border-radius: 50%; min-width: 20px; height: 20px;
  display: flex; align-items: center; justify-content: center; font-size: 10px; flex-shrink: 0;
}
.val-msg { color: #333; word-break: break-all; white-space: pre-line; }

/* 评论样式 */
.comments-section { overflow-y: auto; }
.cmt-card { margin-bottom: 4px; padding: 10px 0; border-bottom: 1px solid #f5f5f5; }
.cmt-card:last-child { border-bottom: none; }
.cmt-node-link {
  font-size: 12px; color: #1677ff; cursor: pointer; margin-bottom: 6px;
  padding: 4px 8px; background: #f0f5ff; border-radius: 4px; display: flex; align-items: flex-start;
  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; line-height: 1.5;
}
.cmt-node-link:hover { background: #e6f0ff; }
.cmt-main { display: flex; gap: 8px; }
.cmt-avatar {
  width: 28px; height: 28px; border-radius: 50%; background: #1677ff; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; flex-shrink: 0;
}
.cmt-avatar.sm { width: 22px; height: 22px; font-size: 10px; background: #8c8c8c; }
.cmt-content { flex: 1; min-width: 0; }
.cmt-meta { display: flex; align-items: baseline; gap: 6px; }
.cmt-meta strong { font-size: 13px; }
.cmt-time { font-size: 11px; color: #999; }
.cmt-text { font-size: 13px; color: #333; margin: 3px 0; white-space: pre-wrap; word-break: break-all; line-height: 1.5; }
.cmt-actions { display: flex; gap: 10px; margin-top: 2px; }
.cmt-actions span { font-size: 12px; color: #8c8c8c; cursor: pointer; }
.cmt-actions span:hover { color: #1677ff; }
.cmt-actions .danger { color: #ff4d4f; }
.cmt-actions .danger:hover { color: #cf1322; }
.cmt-actions .resolve-btn { color: #1677ff; }
.cmt-actions .resolve-btn:hover { color: #0958d9; }
.cmt-actions .resolved { color: #52c41a; }
.cmt-replies { padding-left: 36px; margin-top: 6px; }
.cmt-reply { margin-top: 6px; }
.cmt-reply-box { padding-left: 36px; margin-top: 6px; }
.cmt-input-area { margin-top: 8px; border-top: 1px solid #f0f0f0; padding-top: 8px; flex-shrink: 0; }

/* 冲突解决卡片 */
.conflict-card {
  flex: 1; border: 2px solid #f0f0f0; border-radius: 8px; padding: 16px;
  cursor: pointer; transition: all .2s;
}
.conflict-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.conflict-card-title { font-weight: 600; font-size: 15px; margin-bottom: 6px; }
.conflict-card-desc { color: #666; font-size: 13px; }
.conflict-local { border-color: #f56c6c; }
.conflict-local .conflict-card-title { color: #f56c6c; }
.conflict-server { border-color: #1677ff; }
.conflict-server .conflict-card-title { color: #1677ff; }
.conflict-merge { border-color: #52c41a; }
.conflict-merge .conflict-card-title { color: #52c41a; }

/* 差异对比 */
.diff-progress { display: flex; gap: 4px; margin-bottom: 16px; flex-wrap: wrap;padding-left: 3px; }
.diff-dot {
  width: 16px; height: 16px; border-radius: 50%; background: #e4e7ed; cursor: pointer;
  transition: all .2s; border: 2px solid transparent;
}
.diff-dot.active { border-color: #1677ff; transform: scale(1.3); }
.diff-dot.chosen.local { background: #f56c6c; }
.diff-dot.chosen.server { background: #1677ff; }
.diff-panel { display: flex; gap: 12px; }
.diff-col {
  flex: 1; border: 2px solid #e4e7ed; border-radius: 8px; overflow: hidden;
  cursor: pointer; transition: all .2s;
}
.diff-col:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.diff-col.selected { border-color: #1677ff; box-shadow: 0 0 0 2px rgba(22,119,255,0.15); }
.diff-col-header { padding: 8px 12px; font-weight: 600; font-size: 13px; color: #fff; }
.diff-col-header.local { background: #f56c6c; }
.diff-col-header.server { background: #1677ff; }
.diff-col-body { padding: 12px; }
.diff-field { font-size: 13px; color: #333; margin-bottom: 6px; line-height: 1.6; word-break: break-all; }
.diff-label { color: #909399; font-size: 12px; }
</style>

<style>
/* simple-mind-map 基础边框 (不使用!important, 让标记内联样式优先) */
.smm-node .smm-node-shape { stroke: #c9ced6; stroke-width: 1px; }
.smm-node.active .smm-node-shape { stroke: #1677ff; stroke-width: 2px; }
.smm-node:hover .smm-node-shape { stroke: #4096ff; }
.smm-root-node .smm-node-shape { stroke: #1677ff; stroke-width: 2px; }
</style>

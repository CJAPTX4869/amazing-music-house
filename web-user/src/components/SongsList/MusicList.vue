<script lang="tsx">
/* eslint-disable consistent-return */
import { useDbClickPlay } from '@/hook/useDbClickPlay';
import { useMainStore } from '@/stores/main';
import { formateSongsAuthor } from '@/utils';
import { formatCount } from '@/utils/commentContent';
import { ChatbubbleEllipsesOutline, Heart } from '@vicons/ionicons5';
import { VolumeMuteFilled, VolumeUpFilled } from '@vicons/material';
import { DeleteOutlined } from '@vicons/antd';
import { useThemeVars, type DataTableColumns } from 'naive-ui';
import { NIcon, NTime, NTag } from 'naive-ui';
import { computed, defineComponent } from 'vue';
import { useRouter } from 'vue-router';

type RowData = {
  like: boolean;
  ar: any[]
  al: { name: string, nameRichText: string };
  dt: number;
  name: string;
  nameRichText: string;
  formatAuthorRichText: string;
  mv: number;
  id: string;
  // 免费或无版权1: VIP 歌曲4: 购买专辑8: 非会员可免费播放低音质，会员可播放高音质及下载 
  //fee 为 1 或 8 的歌曲均可单独购买 2 元单曲
  fee: number;
  isSearch?: boolean;
  /** 是否显示「移除」按钮（仅本人歌单/我喜欢的音乐传 true） */
  removable?: boolean;
  index?: number;
  rawIndex: number;
  /** 喜欢数（V3.1 角标，legacy 接口带回） */
  likeCount?: number;
  /** 评论数（V3.1 角标，legacy 接口带回） */
  commentCount?: number;
}

export default defineComponent({
  props: {
    songList: { type: Array, default: () => [] },
    loading: { type: Boolean },
    playListId: { type: String, required: true },
    rawSongList: { type: Array, default: () => [] },
    // 仅本人歌单/我喜欢的音乐传 true，才显示「移除」按钮
    removable: { type: Boolean, default: false }
  },
  emits: ['updateMusicListLike', 'remove'],
  setup(props, ctx) {
    const themeVars = useThemeVars();
    const mainStore = useMainStore();
    const router = useRouter();
    // 评论角标 → 歌曲评论页（V3.1：网易云灵魂入口）
    const goComment = (row: RowData) => router.push(`/comment/song/${row.id}`);
    const badgeStyle = {
      display: 'inline-flex',
      alignItems: 'center',
      gap: '3px',
      marginLeft: '10px',
      fontSize: '12px',
      opacity: 0.55,
      whiteSpace: 'nowrap' as const
    };
    const tagColor = computed(() => {
      return {
        textColor: themeVars.value.primaryColor,
        borderColor: themeVars.value.primaryColor
      };
    });
    const columns: DataTableColumns<RowData> = [
      {
        title: '操作',
        key: 'row',
        width: 72,
        render(row, index) {
          let songList = (props.rawSongList.length
            ? props.rawSongList
            : props.songList) as any[];
          let rawIndex = row.isSearch ? index : row.rawIndex;
          return <div class="flex items-center whitespace-nowrap">
            {
              mainStore.currentPlaySong?.id === songList[index].id
                ? <NIcon color={mainStore.playing
                  ? themeVars.value.primaryColor
                  : themeVars.value.textColor1} component={mainStore.playing
                    ? VolumeUpFilled
                    : VolumeMuteFilled} size={20} class="pr-3"></NIcon>
                : <span class="pr-2 opacity-50 whitespace-nowrap">
                  {rawIndex < 9
                    ? '0' + (rawIndex + 1)
                    : (rawIndex + 1)}
                </span>
            }
            {props.removable && (
              <NIcon
                size={18}
                class="ml-3 cursor-pointer opacity-60 hover:opacity-100"
                title="从歌单移除"
                style={{ cursor: 'pointer' }}
                onClick={(e: MouseEvent) => { e.stopPropagation(); ctx.emit('remove', row.id); }}
                component={DeleteOutlined}
              />
            )}
            <heart-icon
              id={row.id}
              like={row.like}
              size={20} likeSuccess={(like: boolean) => likeSuccess(like, index)}
            />
          </div>;
        }
      },
      {
        title: '标题',
        key: 'name',
        width: '200',
        render(row) {
          return <div class="cursor-pointer">
            {row.nameRichText
              ? <span v-html={row.nameRichText}></span>
              : row.name}
            {row.mv
              ? <NTag size="small" color={tagColor.value} class="ml-2">MV</NTag>
              : null}
            {row.fee === 0
              ? <NTag size="small" class="ml-2">无音源</NTag>
              : null}
            {row.fee === 1
              ? <NTag size="small" color={tagColor.value} class="ml-2">VIP</NTag>
              : null}
            {/* 喜欢数/评论数角标（网易云样式；评论角标可点击跳评论页） */}
            <span style={badgeStyle} title={`${row.likeCount ?? 0} 人喜欢`}>
              <NIcon size={12} component={Heart} />
              {formatCount(row.likeCount)}
            </span>
            <span style={{ ...badgeStyle, cursor: 'pointer' }} title="查看评论" onClick={() => goComment(row)}>
              <NIcon size={12} component={ChatbubbleEllipsesOutline} />
              {formatCount(row.commentCount)}
            </span>

          </div>;
        }
      },
      {
        title: '歌手',
        key: 'singer',
        width: '120',
        render(row) {
          return <p class="text-sm truncate">
            {row.formatAuthorRichText
              ? <span v-html={row.formatAuthorRichText}></span>
              : formateSongsAuthor(row.ar)}
          </p>;
        }
      },
      {
        title: '专辑',
        key: 'album',
        width: '120',
        render(row) {
          return <p class="text-sm truncate">
            {row.al.name
              ? row.al.nameRichText
                ? <span v-html={row.al.nameRichText}></span>
                : row.al.name
              : <span class="opacity-50">未知专辑</span>}
          </p>;
        }
      },
      {
        title: '时间',
        key: 'time',
        width: 80,
        render(row) {
          return <NTime
            class="text-sm text-left"
            time={row.dt}
            format="mm:ss"
          />;
        }
      }
    ];
    const rowClassName = (_row: any, index: number) => {
      if (index === +mainStore.currentPlayIndex) {
        return 'current-play bg-gray-200/80 dark:bg-gray-200/20';
      }
      return '';
    };
    const likeSuccess = (like: boolean, index: number) => {
      ctx.emit(
        'updateMusicListLike', like, index
      );
    };
    const handleClick = useDbClickPlay();
    return () => {
      return <div >
        <n-data-table
          striped
          columns={columns}
          data={props.songList}
          row-class-name={rowClassName}
          virtual-scroll
          max-height={650}
          loading={props.loading}
          row-props={(row: any, index: number) => {
            let rawIndex = row.isSearch ? index : row.rawIndex;
            return {
              ondblclick: () => {
                let findIndex = mainStore.playList.findIndex(item => item.id === row.id)
                handleClick(
                  props.rawSongList.length
                    ? props.rawSongList
                    : props.songList, props.playListId, row, findIndex !== -1 ? findIndex : rawIndex
                )
              }
            };
          }}
        />
      </div>;
    };
  }
});
</script>

<style scoped>
:deep(.n-data-table-th:first-child) {
  text-align: center;
}

:deep(.n-data-table-thead) {
  background-color: transparent !important;
}

:deep(.n-data-table-thead>.n-data-table-tr) {
  background-color: transparent !important;
}

:deep(.n-data-table .n-data-table-th) {
  background-color: transparent !important;
}

:deep(.n-data-table.n-data-table--bordered .n-data-table-wrapper) {
  border: none;
}
</style>

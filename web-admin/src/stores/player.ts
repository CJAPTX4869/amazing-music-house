import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SongVO } from '@/types'
import { streamUrl } from '@/api/song'

/** 播放模式 */
export type PlayMode = 'order' | 'random' | 'single'

export const usePlayerStore = defineStore('player', () => {
  const currentSong = ref<SongVO | null>(null)
  const playlist = ref<SongVO[]>([])
  const isPlaying = ref(false)
  const volume = ref(0.7)
  const mode = ref<PlayMode>('order')

  /** 当前播放地址 */
  const currentUrl = computed(() => (currentSong.value ? streamUrl(currentSong.value.id) : ''))

  /** 当前歌曲在播放列表中的索引 */
  const currentIndex = computed(() =>
    currentSong.value ? playlist.value.findIndex(s => s.id === currentSong.value!.id) : -1
  )

  /** 播放指定歌曲（若不在列表中则加入列表） */
  function play(song: SongVO) {
    if (!playlist.value.some(s => s.id === song.id)) {
      playlist.value.push(song)
    }
    if (currentSong.value?.id !== song.id) {
      currentSong.value = song
    }
    isPlaying.value = true
  }

  /** 用整个列表播放（点击"播放全部"） */
  function playList(songs: SongVO[], startIndex = 0) {
    playlist.value = [...songs]
    currentSong.value = songs[startIndex] ?? null
    isPlaying.value = true
  }

  /** 下一首 */
  function next() {
    if (!playlist.value.length) return
    let index: number
    if (mode.value === 'random') {
      if (playlist.value.length > 1) {
        // 随机换曲时排除当前歌曲，从其余候选索引中随机
        const candidates = playlist.value
          .map((_, i) => i)
          .filter(i => i !== currentIndex.value)
        index = candidates[Math.floor(Math.random() * candidates.length)]
      } else {
        // 仅剩一首时保持当前曲（当前曲不在列表中则从头开始）
        index = currentIndex.value >= 0 ? currentIndex.value : 0
      }
    } else {
      index = (currentIndex.value + 1) % playlist.value.length
    }
    currentSong.value = playlist.value[index]
    isPlaying.value = true
  }

  /** 上一首 */
  function prev() {
    if (!playlist.value.length) return
    const index =
      currentIndex.value <= 0 ? playlist.value.length - 1 : currentIndex.value - 1
    currentSong.value = playlist.value[index]
    isPlaying.value = true
  }

  /** 从播放列表移除 */
  function remove(id: number) {
    playlist.value = playlist.value.filter(s => s.id !== id)
    if (currentSong.value?.id === id) {
      currentSong.value = playlist.value[0] ?? null
      isPlaying.value = false
    }
  }

  function setVolume(v: number) {
    volume.value = v
  }

  function toggleMode() {
    mode.value = mode.value === 'order' ? 'random' : mode.value === 'random' ? 'single' : 'order'
  }

  return {
    currentSong,
    playlist,
    isPlaying,
    volume,
    mode,
    currentUrl,
    currentIndex,
    play,
    playList,
    next,
    prev,
    remove,
    setVolume,
    toggleMode
  }
})

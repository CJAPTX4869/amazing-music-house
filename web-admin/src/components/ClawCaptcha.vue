<template>
  <!--
    ClawCaptcha · Vue 3 移植版（源自开源 PlayCaptcha，MIT License）
    抓娃娃机人机验证：摇杆/方向键移动 → 抓取 → 送到投放区放下。
    抓对目标玩具 = 验证通过（emit('verify')，仅触发一次）。
    零运行时依赖：rAF 物理循环 + CSS 过渡。
  -->
  <div
    class="clawcap"
    role="group"
    aria-label="抓娃娃机人机验证"
    tabindex="0"
    @keydown="onKeyDown"
    @keyup="onKeyUp"
  >
    <header class="clawcap-top">
      <span :class="verified ? 'clawcap-shield clawcap-shield--ok' : 'clawcap-shield'" aria-hidden="true">
        <svg viewBox="0 0 20 20" width="14" height="14">
          <path d="M10 2.2 4 4.6v4.6c0 4 2.6 6.7 6 8.2 3.4-1.5 6-4.2 6-8.2V4.6Z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
          <path v-if="verified" d="m7 9.8 2.2 2.2L13.4 7.6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </span>
      <button type="button" class="clawcap-help" aria-label="关于抓娃娃验证" aria-haspopup="dialog" @click="infoOpen = true">
        <svg viewBox="0 0 20 20" width="14" height="14" aria-hidden="true">
          <circle cx="10" cy="10" r="7.4" fill="none" stroke="currentColor" stroke-width="1.4" />
          <path d="M8 8.2c.2-1.2 1-1.9 2.1-1.9 1.2 0 2 .8 2 1.8 0 1.6-2.1 1.7-2.1 3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
          <circle cx="10" cy="13.9" r="0.9" fill="currentColor" />
        </svg>
      </button>
    </header>

    <Transition name="ccfade">
      <div v-if="infoOpen" class="clawcap-info" role="dialog" aria-modal="true" aria-label="关于抓娃娃验证" @click="infoOpen = false">
        <div class="clawcap-info-card" @click.stop>
          <button type="button" class="clawcap-info-x" aria-label="关闭" @click="infoOpen = false">
            <svg viewBox="0 0 20 20" width="14" height="14" aria-hidden="true">
              <path d="M5 5l10 10M15 5 5 15" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
            </svg>
          </button>
          <div class="clawcap-info-head">
            <span class="clawcap-info-tile"><img :src="assetBase + '../playcaptcha.svg'" alt="" aria-hidden="true" /></span>
            <h4 class="clawcap-info-title">抓娃娃验证 <span class="clawcap-info-ver">v1</span></h4>
            <p class="clawcap-info-tag">抓对玩具，证明你是人类。</p>
          </div>
          <ol class="clawcap-info-list">
            <li v-for="(s, i) in infoSteps" :key="s.k">
              <span class="clawcap-info-n">{{ i + 1 }}</span>
              <span>
                <strong>{{ s.k }}</strong>
                <span class="clawcap-info-d">{{ s.d }}</span>
              </span>
            </li>
          </ol>
          <button type="button" class="clawcap-info-done" @click="infoOpen = false">知道了</button>
        </div>
      </div>
    </Transition>

    <h3 class="clawcap-title">
      <Transition name="ccxf" mode="out-in">
        <span :key="verified ? 'ok' : 't'" class="cc-xfspan">{{ verified ? '验证通过' : title }}</span>
      </Transition>
    </h3>
    <p class="clawcap-sub" aria-live="polite">
      <Transition name="ccxf" mode="out-in">
        <span :key="subKey" class="cc-xfspan">
          <template v-if="verified">你是人类，抓得漂亮！</template>
          <template v-else-if="message">{{ message }}</template>
          <template v-else>
            用抓娃娃机抓起
            <em :style="{ color: targetMeta.accent }">
              <img class="clawcap-sub-toy" :src="assetBase + target + '.png'" alt="" draggable="false" />{{ targetMeta.label }}
            </em>
          </template>
        </span>
      </Transition>
    </p>

    <ol class="clawcap-steps" aria-hidden="true">
      <span class="clawcap-steps-pill" :style="{ transform: `translateX(${(stepNo - 1) * 100}%)` }" />
      <li v-for="(lb, i) in ['移动', '抓取', '投放']" :key="lb" :class="stepNo === i + 1 ? 'is-active' : ''">
        <span class="clawcap-step-n">{{ i + 1 }}</span> {{ lb }}
      </li>
    </ol>

    <div ref="machineEl" class="clawcap-machine">
      <div class="clawcap-case">
        <div :class="verified ? 'clawcap-glass clawcap-glass--dim' : 'clawcap-glass'">
          <div class="cc-rail" />
          <div ref="trolleyEl" class="cc-trolley" aria-hidden="true" />
          <div ref="shadowEl" class="cc-claw-shadow" aria-hidden="true" />

          <img
            v-for="(p, i) in pile"
            :key="p.toy"
            :ref="(el) => { pileEls[i] = el as HTMLImageElement | null }"
            class="cc-toy"
            :src="assetBase + p.toy + '.png'"
            alt=""
            draggable="false"
            :style="{ left: p.x - p.w / 2 + 'px', bottom: p.b + 'px', width: p.w + 'px', zIndex: p.z, transform: `translateY(${p.dropFrom}px)`, transformOrigin: '50% 100%' }"
          />
          <div class="cc-pile-shadow" />

          <svg ref="rigEl" class="cc-rig" width="36" :height="COIL_LEN + 70" :viewBox="`0 0 36 ${COIL_LEN + 70}`" aria-hidden="true">
            <g ref="coilEl" :transform="`translate(9 0) scale(1 ${COIL_LEN / 100})`">
              <path
                d="M9 0 L9 5 C 15 7.5 15 9.5 9 12 C 3 14.5 3 16.5 9 19 C 15 21.5 15 23.5 9 26 C 3 28.5 3 30.5 9 33 C 15 35.5 15 37.5 9 40 C 3 42.5 3 44.5 9 47 C 15 49.5 15 51.5 9 54 C 3 56.5 3 58.5 9 61 C 15 63.5 15 65.5 9 68 C 3 70.5 3 72.5 9 75 C 15 77.5 15 79.5 9 82 C 3 84.5 3 86.5 9 89 C 15 91.5 15 93.5 9 95 L 9 100"
                fill="none" stroke="#9A9FA8" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke"
              />
            </g>
            <g ref="clawEl" :transform="`translate(18 ${COIL_LEN}) scale(2.5) translate(-20.6 -8.9)`">
              <g ref="fingerLEl"><path v-for="(pa, i) in CLAW_ARM_L" :key="i" :fill="pa.fill" :d="pa.d" /></g>
              <g ref="fingerREl"><path v-for="(pa, i) in CLAW_ARM_R" :key="i" :fill="pa.fill" :d="pa.d" /></g>
              <path v-for="(pa, i) in CLAW_BODY" :key="i" :fill="pa.fill" :d="pa.d" />
            </g>
          </svg>

          <div class="cc-glass-shine" />
        </div>

        <div class="clawcap-panel">
          <div
            class="cc-joy"
            role="slider"
            aria-label="移动爪子"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="Math.round(((sim.x - CLAW_MIN) / (CLAW_MAX - CLAW_MIN)) * 100)"
            @pointerdown="onStickDown"
            @pointermove="onStickMove"
            @pointerup="onStickUp"
            @pointercancel="onStickUp"
          >
            <div class="cc-joy-base" />
            <div ref="stickEl" class="cc-joy-stick">
              <div class="cc-joy-shaft" />
              <div class="cc-joy-ball" />
            </div>
          </div>

          <div ref="trayEl" :class="trayClass">
            <span class="cc-tray-hatch" aria-hidden="true">
              <span class="cc-tray-mouth" />
              <span class="cc-tray-door cc-tray-door--l" />
              <span class="cc-tray-door cc-tray-door--r" />
              <span class="cc-tray-skin" />
            </span>
            <span v-if="trayMode === 'win' && !reduce" class="cc-confetti" aria-hidden="true">
              <i v-for="(p, i) in CONFETTI" :key="i" :style="{ background: p.c, animationDelay: p.d + 's', '--dx': p.dx + 'px', '--dy': p.dy + 'px', '--dr': p.dr + 'deg' }" />
            </span>
            <span class="cc-tray-label">
              <svg v-if="trayMode === 'win'" viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <path d="m3.6 8.6 2.9 2.9 6-6.8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              <svg v-else-if="trayMode === 'no'" viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <path d="M13.2 8A5.2 5.2 0 1 1 11.6 4.25" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                <path d="M11.7 1.5v2.9h2.9" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              <svg v-else viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <circle cx="8" cy="4.9" r="2.7" fill="none" stroke="currentColor" stroke-width="1.5" />
                <path d="M2.4 12.1h3.7M9.9 12.1h3.7" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
              </svg>
              <span>{{ trayText }}</span>
            </span>
          </div>

          <button
            type="button"
            :class="phase === 'carry' && overTray ? 'cc-action cc-action--ready' : 'cc-action'"
            :disabled="busy || verified"
            :aria-label="phase === 'carry' ? '放下玩具' : '抓取'"
            @click="action"
          >
            <Transition name="ccxf" mode="out-in">
              <span :key="phase === 'carry' ? 'drop' : 'grab'" class="cc-xfspan">{{ phase === 'carry' ? '投放' : '抓取' }}</span>
            </Transition>
          </button>
        </div>
      </div>

      <img
        ref="carriedEl"
        class="cc-carried"
        :src="carriedRef >= 0 ? assetBase + pile[carriedRef].toy + '.png' : assetBase + target + '.png'"
        alt=""
        draggable="false"
        :style="{ width: carriedW + 'px', visibility: carriedRef >= 0 && phase !== 'idle' ? 'visible' : 'hidden' }"
      />
    </div>

    <p class="clawcap-hint">摇杆或 ← → 移动 · 空格键 抓取 / 投放</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/* ---------------- 玩具目录（中文化） ---------------- */
type ToyId = 'duck' | 'bear' | 'panda' | 'bunny' | 'dino' | 'penguin' | 'fox' | 'frog' | 'whale' | 'cat' | 'puppy' | 'unicorn'
const TOY_META: Record<ToyId, { label: string; accent: string }> = {
  duck: { label: '小黄鸭', accent: '#E8A33D' },
  bear: { label: '泰迪熊', accent: '#C98A4B' },
  panda: { label: '熊猫', accent: '#52525B' },
  bunny: { label: '兔子', accent: '#E58AB0' },
  dino: { label: '恐龙', accent: '#5CA86A' },
  penguin: { label: '企鹅', accent: '#3F4854' },
  fox: { label: '狐狸', accent: '#DD7A3D' },
  frog: { label: '青蛙', accent: '#69A85C' },
  whale: { label: '鲸鱼', accent: '#5A93C9' },
  cat: { label: '小猫', accent: '#B08D57' },
  puppy: { label: '小狗', accent: '#A1785A' },
  unicorn: { label: '独角兽', accent: '#B287D8' }
}

/* ---------------- 爪子 SVG 美术数据（自动生成，勿手改） ---------------- */
type ClawPath = { fill: string; d: string }
const CLAW_PIVOT = { x: 20.6, y: 22.7 }
const CLAW_BODY: ClawPath[] = [{"fill":"#666D85","d":"M20.031,24.324c0.086,2.939,0.263,5.874,0.531,8.802c0.016,0.175,0.073,0.395,0.247,0.42c0.218,0.03,0.311-0.267,0.318-0.487c0.104-2.988,0.207-5.975,0.311-8.963C21.048,24.142,20.657,24.188,20.031,24.324z"},{"fill":"#3D4459","d":"M20.222,11.816c0.023,2.291,0.046,4.583,0.069,6.874c0.356,0.036,0.717,0.036,1.073,0c-0.01-2.339-0.019-4.679-0.028-7.018C20.987,11.656,20.636,11.679,20.222,11.816z"},{"fill":"#666D85","d":"M18.924,10.588c0.013,0.395,0.025,0.789,0.038,1.184c0.003,0.101,0.009,0.209,0.073,0.288c0.084,0.103,0.234,0.116,0.367,0.12c1.017,0.034,2.044,0.068,3.047-0.106c0.054-0.009,0.111-0.021,0.15-0.059c0.047-0.047,0.054-0.12,0.058-0.187c0.022-0.45,0.009-0.901-0.039-1.349c-0.004-0.04-0.011-0.083-0.04-0.11c-0.031-0.03-0.079-0.031-0.122-0.031C21.186,10.343,19.916,10.39,18.924,10.588z"},{"fill":"#DE2121","d":"M18.282,9.052c-0.062,0-0.13,0.002-0.179,0.04c-0.059,0.045-0.074,0.126-0.084,0.199c-0.055,0.411-0.06,0.83-0.015,1.242c0.008,0.074,0.02,0.153,0.071,0.208c0.066,0.07,0.172,0.077,0.268,0.079c1.573,0.031,3.146,0.006,4.717-0.076c0.064-0.003,0.132-0.008,0.185-0.045c0.096-0.066,0.102-0.204,0.098-0.321c-0.015-0.438-0.031-0.875-0.046-1.313c-0.001-0.039-0.004-0.081-0.031-0.109c-0.029-0.03-0.076-0.032-0.119-0.032C22.905,8.925,19.701,9.046,18.282,9.052z"},{"fill":"#666D85","d":"M19.815,20.079c-0.001-0.526-0.003-1.052-0.004-1.577c0-0.036,0-0.074,0.017-0.105c0.033-0.061,0.112-0.077,0.18-0.085c0.483-0.058,0.97-0.086,1.456-0.083c0.099,0.001,0.21,0.008,0.274,0.083c0.053,0.062,0.055,0.151,0.055,0.233c0,0.475-0.001,0.95-0.001,1.425C21.155,19.978,20.517,19.972,19.815,20.079z"},{"fill":"#B4B9C4","d":"M19.458,20.771c-0.269,0.791-0.539,1.583-0.808,2.374c-0.016,0.048-0.033,0.098-0.022,0.147c0.01,0.047,0.045,0.085,0.077,0.121l0.799,0.87c0.083,0.091,0.169,0.184,0.28,0.238c0.134,0.066,0.289,0.068,0.438,0.069c0.471,0.003,0.942,0.006,1.413,0.01c0.048,0,0.098,0,0.141-0.021c0.052-0.026,0.085-0.078,0.115-0.127c0.244-0.399,0.487-0.797,0.731-1.196c0.047-0.078,0.096-0.159,0.106-0.249c0.011-0.101-0.027-0.199-0.065-0.293c-0.231-0.58-0.463-1.159-0.695-1.739c-0.029-0.072-0.061-0.149-0.124-0.194c-0.074-0.053-0.172-0.051-0.262-0.047C20.836,20.765,20.09,20.808,19.458,20.771z"},{"fill":"#3D4459","d":"M21.101,22.267c-0.117-0.169-0.332-0.237-0.531-0.186c-0.182,0.047-0.359,0.12-0.459,0.274c-0.076,0.116-0.095,0.259-0.101,0.397c-0.004,0.095-0.002,0.192,0.034,0.28c0.061,0.149,0.214,0.245,0.372,0.274c0.254,0.047,0.53-0.066,0.678-0.278c0.147-0.212,0.158-0.51,0.025-0.732C21.114,22.286,21.107,22.276,21.101,22.267z"},{"fill":"#DE2121","d":"M22.585,19.777c-1.368-0.115-2.751-0.055-4.105,0.179c-0.127,0.022-0.272,0.059-0.327,0.176c-0.028,0.06-0.025,0.129-0.022,0.195c0.009,0.173,0.017,0.345,0.026,0.518c0.003,0.058,0.007,0.118,0.037,0.167c0.053,0.084,0.164,0.106,0.262,0.118c1.395,0.173,2.808,0.083,4.21-0.007c0.351-0.023,0.703-0.045,1.047-0.121c0.053-0.012,0.109-0.026,0.145-0.066c0.044-0.048,0.049-0.119,0.05-0.184c0.003-0.209-0.01-0.418-0.039-0.625c-0.01-0.07-0.025-0.145-0.076-0.193c-0.048-0.045-0.118-0.055-0.183-0.064C23.273,19.826,22.935,19.806,22.585,19.777z"},{"fill":"#851313","d":"M20.275,21.326c-0.53,0-1.06-0.021-1.589-0.064c-0.113-0.009-0.267-0.021-0.391-0.104c-0.281-0.187-0.251-0.584-0.204-0.866c0.007-0.041,0.046-0.068,0.086-0.061c0.041,0.007,0.068,0.045,0.062,0.086c-0.064,0.383-0.02,0.611,0.139,0.716c0.085,0.056,0.195,0.069,0.32,0.079c1.626,0.13,3.266,0.058,4.875-0.214c0.041-0.007,0.079,0.021,0.086,0.061c0.007,0.041-0.021,0.08-0.061,0.087C22.501,21.233,21.388,21.326,20.275,21.326z"},{"fill":"#1F2229","d":"M20.376,17.8c-0.04,0-0.074-0.032-0.075-0.073l-0.152-5.054c-0.001-0.041,0.031-0.076,0.073-0.077c0.042,0,0.076,0.031,0.077,0.073l0.152,5.054c0.001,0.042-0.031,0.076-0.073,0.077C20.377,17.8,20.376,17.8,20.376,17.8z"},{"fill":"#851313","d":"M17.993,10.472c-0.041,0-0.075-0.033-0.075-0.075l-0.001-0.949c0-0.109,0-0.258,0.087-0.372c0.126-0.164,0.364-0.165,0.492-0.166l4.541-0.022c0.088-0.001,0.179,0.004,0.25,0.068c0.089,0.081,0.087,0.211,0.086,0.282l-0.018,1.146c-0.001,0.042-0.033,0.074-0.076,0.074c-0.042-0.001-0.075-0.035-0.074-0.076l0.018-1.146c0.001-0.064-0.001-0.136-0.037-0.168c-0.031-0.028-0.087-0.03-0.148-0.03l-4.541,0.022c-0.141,0.001-0.298,0.01-0.374,0.108c-0.052,0.068-0.056,0.167-0.056,0.28l0.001,0.949C18.068,10.438,18.035,10.472,17.993,10.472C17.993,10.472,17.993,10.472,17.993,10.472z"}]
const CLAW_ARM_L: ClawPath[] = [{"fill":"#808596","d":"M18.965,20.803c-1.243,0.927-2.486,1.854-3.729,2.781c-0.176,0.131-0.363,0.279-0.417,0.491c-0.049,0.192,0.023,0.391,0.089,0.577c0.681,1.927,0.824,3.999,1.323,5.981c0.055,0.217,0.118,0.441,0.261,0.613c0.142,0.17,0.348,0.272,0.542,0.38c0.641,0.359,1.208,0.848,1.658,1.429c0.141,0.182,0.283,0.383,0.499,0.463c0.216,0.08,0.52-0.043,0.533-0.249c-0.459-0.883-1.167-1.635-2.02-2.147c-0.152-0.091-0.313-0.179-0.413-0.325c-0.081-0.119-0.113-0.263-0.142-0.404c-0.348-1.641-0.697-3.281-1.045-4.922c-0.102-0.478,0.135-0.964,0.574-1.178c0.616-0.3,1.225-0.615,1.827-0.943c0.325-0.177,0.656-0.367,0.882-0.66c0.274-0.355,0.358-0.817,0.433-1.259c0.029-0.17,0.057-0.35-0.007-0.51c-0.096-0.238-0.378-0.358-0.635-0.345C18.921,20.591,18.683,20.709,18.965,20.803z"},{"fill":"#3D4459","d":"M19.335,32.718c-0.024,0-0.047-0.011-0.062-0.032c-0.347-0.498-0.802-0.934-1.314-1.26c-0.033-0.021-0.067-0.042-0.101-0.063c-0.213-0.132-0.432-0.268-0.574-0.485c-0.11-0.169-0.158-0.367-0.2-0.543c-0.391-1.627-0.752-3.283-1.073-4.922c-0.032-0.163-0.076-0.387,0.009-0.583c0.093-0.216,0.315-0.338,0.477-0.427l1.805-0.99c0.036-0.02,0.082-0.007,0.102,0.03c0.02,0.036,0.007,0.082-0.03,0.102l-1.805,0.99c-0.185,0.102-0.344,0.199-0.412,0.355c-0.061,0.141-0.036,0.307,0.001,0.494c0.321,1.638,0.681,3.292,1.072,4.916c0.039,0.163,0.084,0.349,0.18,0.496c0.123,0.188,0.328,0.316,0.527,0.439c0.034,0.021,0.068,0.042,0.102,0.064c0.529,0.336,0.998,0.786,1.357,1.3c0.024,0.034,0.015,0.081-0.019,0.104C19.365,32.714,19.35,32.718,19.335,32.718z"}]
const CLAW_ARM_R: ClawPath[] = [{"fill":"#808596","d":"M22.64,20.803c1.243,0.927,2.486,1.854,3.729,2.781c0.176,0.131,0.363,0.279,0.417,0.491c0.049,0.192-0.023,0.391-0.089,0.577c-0.681,1.927-0.824,3.999-1.323,5.981c-0.055,0.217-0.118,0.441-0.261,0.613c-0.142,0.17-0.348,0.272-0.542,0.38c-0.641,0.359-1.208,0.848-1.658,1.429c-0.141,0.182-0.284,0.383-0.499,0.463c-0.216,0.08-0.52-0.043-0.533-0.249c0.459-0.883,1.167-1.635,2.02-2.147c0.152-0.091,0.313-0.179,0.413-0.325c0.081-0.119,0.113-0.263,0.142-0.404c0.348-1.641,0.697-3.281,1.045-4.922c0.101-0.478-0.135-0.964-0.574-1.178c-0.616-0.3-1.225-0.615-1.827-0.943c-0.324-0.177-0.656-0.367-0.882-0.66c-0.274-0.355-0.358-0.817-0.433-1.259c-0.029-0.17-0.057-0.35,0.007-0.51c0.096-0.238,0.378-0.358,0.635-0.345C22.684,20.591,22.922,20.709,22.64,20.803z"},{"fill":"#3D4459","d":"M24.41,30.372c-0.007,0-0.014-0.001-0.021-0.003c-0.04-0.012-0.063-0.053-0.051-0.093c0.464-1.586,0.83-3.218,1.089-4.85c0.007-0.041,0.045-0.069,0.086-0.062c0.041,0.007,0.069,0.045,0.062,0.086c-0.26,1.639-0.627,3.277-1.093,4.868C24.472,30.351,24.442,30.372,24.41,30.372z"},{"fill":"#3D4459","d":"M22.613,33.409c-0.017,0-0.034-0.006-0.048-0.017c-0.032-0.027-0.036-0.074-0.009-0.106c0.565-0.675,1.234-1.247,1.99-1.698l0.055-0.033c0.18-0.107,0.365-0.217,0.488-0.377c0.142-0.186,0.197-0.436,0.244-0.656l1.14-5.256c0.009-0.04,0.049-0.066,0.089-0.057c0.041,0.009,0.066,0.049,0.057,0.089l-1.14,5.256c-0.051,0.236-0.109,0.503-0.272,0.715c-0.141,0.184-0.339,0.301-0.531,0.415l-0.055,0.032c-0.741,0.443-1.398,1.003-1.952,1.666C22.656,33.399,22.635,33.409,22.613,33.409z"}]

/* ---------------- 几何常量（380px 宽机身坐标系） ---------------- */
const GW = 380
const GH = 320
const RAIL_Y = 14
const HOME_Y = 64
const DROP_Y = 198
const CLAW_MIN = 46
const CLAW_MAX = 334
const COIL_LEN = 50
const GRAB_RADIUS = 38
const GRIP_OFFSET = 46
const TRAY = { cx: 232, cy: GH + 56, min: 150, max: 320 }

const TOY_SET: Array<{ toy: ToyId; w: number }> = [
  { toy: 'duck', w: 96 }, { toy: 'bear', w: 92 }, { toy: 'panda', w: 86 },
  { toy: 'bunny', w: 78 }, { toy: 'dino', w: 92 }, { toy: 'penguin', w: 84 },
  { toy: 'fox', w: 80 }, { toy: 'frog', w: 76 }, { toy: 'whale', w: 90 },
  { toy: 'cat', w: 74 }, { toy: 'puppy', w: 72 }, { toy: 'unicorn', w: 82 }
]
type Slot = { toy: ToyId; w: number; x: number; b: number; z: number; rot: number; dropFrom: number; delay: number }

const rand = (a: number, b: number) => a + Math.random() * (b - a)

const CONFETTI = [
  { dx: -44, dy: -54, dr: -150, c: '#34c759', d: 0 },
  { dx: -30, dy: -66, dr: 120, c: '#ffd60a', d: 0.05 },
  { dx: -14, dy: -76, dr: -80, c: '#5cd679', d: 0.02 },
  { dx: 2, dy: -80, dr: 60, c: '#5a93c9', d: 0.07 },
  { dx: 16, dy: -74, dr: -130, c: '#ffb340', d: 0.03 },
  { dx: 30, dy: -64, dr: 100, c: '#a8e6b8', d: 0.06 },
  { dx: 44, dy: -52, dr: -110, c: '#34c759', d: 0.01 },
  { dx: -54, dy: -36, dr: 90, c: '#e58ab0', d: 0.09 },
  { dx: 54, dy: -34, dr: -70, c: '#5a93c9', d: 0.08 }
]

const easeInQuad = (p: number) => p * p
const easeOutCubic = (p: number) => 1 - Math.pow(1 - p, 3)
const easeInOutCubic = (p: number) => (p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2)
const clamp01 = (p: number) => Math.min(1, Math.max(0, p))

const T = {
  antic: 0.16, down: 0.78, dwell1: 0.18, close: 0.45,
  dwell2: 0.26, load: 0.24, up: 0.95, open: 0.4
}
const ANTIC_RISE = 8
const DROP_G = 1150
const ENTRANCE_G = 1500

function shuffle<T>(arr: T[]): T[] {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

/** 每次挂载重新撒一堆玩具（前排落地、后排垫高，目标玩具周围留空好抓） */
function scatterPile(target: ToyId): Slot[] {
  const order = shuffle(TOY_SET)
  const rest = order.filter((t) => t.toy !== target)
  const tgt = order.find((t) => t.toy === target)!
  const nB = 8
  const nTop = order.length - nB
  const bottomIdx = 2 + Math.floor(Math.random() * 4)
  const slots: Slot[] = new Array(order.length)

  let r = 0
  const frontW: number[] = []
  const frontToy: Array<{ toy: ToyId; w: number }> = []
  for (let i = 0; i < nB; i++) {
    const isTarget = i === bottomIdx
    const t = isTarget ? tgt : rest[r++]!
    frontToy.push(t)
    frontW.push(t.w * (isTarget ? rand(1.0, 1.05) : rand(0.76, 0.86)))
  }
  const xs: number[] = [0]
  for (let i = 1; i < nB; i++) {
    xs.push(xs[i - 1]! + ((frontW[i - 1]! + frontW[i]!) / 2) * rand(0.62, 0.68))
  }
  const span = xs[nB - 1]!
  const fit = Math.min(1, (GW - 80) / span)
  for (let i = 0; i < nB; i++) frontW[i] = frontW[i]! * fit
  const offset = (GW - span * fit) / 2
  const centers: number[] = []
  for (let i = 0; i < nB; i++) {
    const cx = offset + xs[i]! * fit + rand(-3, 3)
    centers.push(cx)
    const isTarget = i === bottomIdx
    slots[i] = {
      toy: frontToy[i]!.toy,
      w: frontW[i]!,
      x: Math.min(GW - 26, Math.max(26, cx)),
      b: rand(0, 2),
      z: isTarget ? 4 : 2,
      rot: rand(-7, 7),
      dropFrom: -rand(340, 440),
      delay: i * 0.05 + rand(0, 0.1)
    }
  }

  const gaps: number[] = []
  for (let g = 0; g < nB - 1; g++) {
    if (g === bottomIdx - 1 || g === bottomIdx) continue
    gaps.push(g)
  }
  const useGaps = shuffle(gaps).slice(0, nTop)
  let ti = 0
  for (const g of useGaps) {
    const t = rest[r++]!
    const cx = (centers[g]! + centers[g + 1]!) / 2 + rand(-3, 3)
    slots[nB + ti] = {
      toy: t.toy,
      w: t.w * rand(0.7, 0.8),
      x: Math.min(GW - 26, Math.max(26, cx)),
      b: rand(6, 16),
      z: 1,
      rot: rand(-8, 8),
      dropFrom: -rand(360, 470),
      delay: 0.45 + ti * 0.08 + rand(0, 0.1)
    }
    ti++
  }
  const tip = slots[nB + Math.floor(Math.random() * nTop)]!
  tip.rot = rand(10, 16) * (Math.random() < 0.5 ? -1 : 1)
  return slots
}

/* ---------------- 组件 ---------------- */
const props = withDefaults(defineProps<{ target?: ToyId; title?: string; assetBase?: string }>(), {
  title: '人机验证',
  assetBase: '/toys/'
})
const emit = defineEmits<{ (e: 'verify'): void }>()

const reduce = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const autoTarget = TOY_SET[Math.floor(Math.random() * TOY_SET.length)]!.toy
const target = (props.target || autoTarget) as ToyId
const targetMeta = TOY_META[target]

const phase = ref<'idle' | 'seq' | 'carry' | 'toTray' | 'celebrate' | 'deny' | 'return' | 'done'>('idle')
const infoOpen = ref(false)
const verified = ref(false)
const message = ref<string | null>(null)
const overTray = ref(false)
const trayMode = ref<'' | 'open' | 'win' | 'no'>('')
const carriedRef = ref(-1)

const infoSteps = [
  { k: '移动', d: '摇杆或 ← → 方向键，把爪子对准目标玩具头顶' },
  { k: '抓取', d: '按红色按钮或空格键，爪子下潜合拢抓起玩具' },
  { k: '投放', d: '运到投放口上方再按一次松手；抓错了会退回玩具堆' }
]

const pile = scatterPile(target)
const targetIdx = pile.findIndex((p) => p.toy === target)

const machineEl = ref<HTMLElement | null>(null)
const trolleyEl = ref<HTMLElement | null>(null)
const shadowEl = ref<HTMLElement | null>(null)
const rigEl = ref<SVGSVGElement | null>(null)
const coilEl = ref<SVGGElement | null>(null)
const clawEl = ref<SVGGElement | null>(null)
const fingerLEl = ref<SVGGElement | null>(null)
const fingerREl = ref<SVGGElement | null>(null)
const carriedEl = ref<HTMLImageElement | null>(null)
const stickEl = ref<HTMLElement | null>(null)
const trayEl = ref<HTMLElement | null>(null)
const pileEls: Array<HTMLImageElement | null> = []

let dir = 0

type Stage = '' | 'antic' | 'down' | 'dwell1' | 'close' | 'dwell2' | 'load' | 'up' | 'open' | 'beat' | 'shine'
const sim = {
  x: GW / 2,
  y: HOME_Y,
  vx: 0,
  drive: 0,
  sway: 0,
  swayV: 0,
  breeze: 0,
  close: 0,
  carried: -1,
  carry: { x: 0, y: 0 },
  stage: '' as Stage,
  st: 0,
  depthY: DROP_Y,
  fallV: 0,
  stretch: 0,
  xrot: 0,
  swallow: 0,
  released: false,
  mouthY: 353
}

type Soft = { dx: number; dy: number; rot: number; sq: number; vdx: number; vdy: number; vrot: number; vsq: number; ey: number; evy: number; delay: number; landed: boolean }
const soft: Soft[] = pile.map((s) => ({
  dx: 0, dy: 0, rot: 0, sq: 0, vdx: 0, vdy: 0, vrot: 0, vsq: 0,
  ey: s.dropFrom, evy: 0, delay: s.delay, landed: false
}))

function setPhaseBoth(p: typeof phase.value) {
  phase.value = p
}

const busy = computed(() => phase.value !== 'idle' && phase.value !== 'carry')
const stepNo = computed(() =>
  verified.value || phase.value === 'carry' || phase.value === 'toTray' || phase.value === 'celebrate' ? 3 : phase.value === 'seq' ? 2 : 1
)
const carriedW = computed(() => (carriedRef.value >= 0 ? pile[carriedRef.value]!.w : 80))
const subKey = computed(() => (verified.value ? 'done' : (message.value ?? 'challenge')))
const trayClass = computed(() =>
  'cc-tray' +
  (trayMode.value === 'open' ? ' cc-tray--open'
    : trayMode.value === 'win' ? ' cc-tray--win'
      : trayMode.value === 'no' ? ' cc-tray--no'
        : overTray.value ? ' cc-tray--hot' : '')
)
const trayText = computed(() =>
  trayMode.value === 'win' ? '抓对了！' : trayMode.value === 'no' ? '哦，抓错了' : overTray.value ? '松手！' : '投放到这里'
)

const toyCenter = (i: number) => {
  const p = pile[i]!
  return { x: p.x, y: GH - p.b - (p.w / 2) * 0.92 }
}

const ripple = (x: number, power: number, except = -1) => {
  pile.forEach((p, i) => {
    if (i === except || i === sim.carried) return
    const d = Math.abs(p.x - x)
    if (d < 80) {
      const f = (1 - d / 80) * power
      const side = p.x < x ? -1 : 1
      const b = soft[i]!
      b.vdx += side * f * 1.6
      b.vdy -= f * 1.1
      b.vrot += side * f * 2
      b.vsq += f * 0.02
    }
  })
}

const pend = () => {
  const len = Math.max(2, sim.y - RAIL_Y)
  const rad = reduce ? 0 : ((sim.sway + sim.breeze) * Math.PI) / 180
  return { ex: sim.x + Math.sin(rad) * len, ey: RAIL_Y + Math.cos(rad) * len }
}
const gripY = (ey: number) => ey + GRIP_OFFSET + (sim.carried >= 0 ? pile[sim.carried]!.w : 80) / 2

const candidateAt = (x: number) => {
  let best = -1
  let bestScore = -Infinity
  pile.forEach((q, i) => {
    const d = Math.abs(q.x - x)
    if (d < GRAB_RADIUS) {
      const score = q.z * 100 - d
      if (score > bestScore) {
        bestScore = score
        best = i
      }
    }
  })
  return best
}

const render = () => {
  const sway = reduce ? 0 : sim.sway + sim.breeze
  const len = Math.max(2, sim.y - RAIL_Y)
  if (trolleyEl.value) trolleyEl.value.style.transform = `translateX(${(sim.x - 14).toFixed(2)}px)`
  if (shadowEl.value) {
    const rad = (sway * Math.PI) / 180
    const ex = sim.x + Math.sin(rad) * len
    const bottomY = sim.carried >= 0 ? sim.carry.y + pile[sim.carried]!.w / 2 : sim.y + 58
    const t = clamp01(1 - (GH - bottomY) / 210)
    shadowEl.value.style.transform = `translateX(${(ex - 45).toFixed(2)}px) scaleX(${(1.25 - 0.5 * t).toFixed(3)})`
    shadowEl.value.style.opacity = (0.1 + 0.3 * t).toFixed(3)
  }
  if (rigEl.value) {
    const totalH = len + 70
    rigEl.value.setAttribute('viewBox', `0 0 36 ${totalH.toFixed(1)}`)
    rigEl.value.setAttribute('height', totalH.toFixed(1))
    rigEl.value.style.transform = `translateX(${sim.x.toFixed(2)}px) rotate(${sway.toFixed(2)}deg)`
  }
  coilEl.value?.setAttribute('transform', `translate(9 0) scale(1 ${(len / 100).toFixed(4)})`)
  clawEl.value?.setAttribute('transform', `translate(18 ${len.toFixed(2)}) scale(2.5) translate(-20.6 -8.9)`)
  const pinch = 15 * sim.close
  fingerLEl.value?.setAttribute('transform', `rotate(${pinch.toFixed(2)} ${CLAW_PIVOT.x} ${CLAW_PIVOT.y})`)
  fingerREl.value?.setAttribute('transform', `rotate(${(-pinch).toFixed(2)} ${CLAW_PIVOT.x} ${CLAW_PIVOT.y})`)
  for (let i = 0; i < pile.length; i++) {
    const el = pileEls[i]
    if (!el) continue
    const b = soft[i]!
    el.style.transform = `translate(${b.dx.toFixed(2)}px, ${(b.dy + b.ey).toFixed(2)}px) rotate(${(pile[i]!.rot + b.rot).toFixed(2)}deg) scale(${(1 - b.sq * 0.6).toFixed(3)}, ${(1 + b.sq).toFixed(3)})`
  }
  if (sim.carried >= 0 && carriedEl.value) {
    const w = pile[sim.carried]!.w
    const sc = 1 - sim.swallow * 0.78
    const sx = (sc * (1 - sim.stretch * 0.55)).toFixed(3)
    const sy = (sc * (1 + sim.stretch)).toFixed(3)
    carriedEl.value.style.transform = `translate(${sim.carry.x - w / 2}px, ${sim.carry.y - w / 2}px) rotate(${(sway + sim.xrot).toFixed(2)}deg) scale(${sx}, ${sy})`
    if (sim.swallow > 0) carriedEl.value.style.opacity = (1 - sim.swallow).toFixed(2)
  }
}

const stageP = (dur: number, dt: number) => {
  sim.st += dt
  return clamp01(sim.st / dur)
}
const nextStage = (st: Stage) => {
  sim.stage = st
  sim.st = 0
}

let raf = 0
function step(now: number) {
  const dtRaw = prevNow ? Math.min(0.04, Math.max(0.004, (now - prevNow) / 1000)) : 1 / 60
  prevNow = now
  const dt = dtRaw * (reduce ? 2.4 : 1)
  const f = dt * 60
  const ph = phase.value

  if (!reduce) {
    const lean = ph === 'idle' || ph === 'carry' ? -sim.vx * 0.042 : 0
    sim.swayV += (lean - sim.sway) * 0.05 * f
    sim.swayV *= Math.pow(0.93, f)
    sim.sway += sim.swayV * f
    sim.breeze = Math.sin(now / 1500) * 0.45 + Math.sin(now / 521) * 0.12
  }

  for (let i = 0; i < soft.length; i++) {
    const b = soft[i]!
    if (!b.landed) {
      if (b.delay > 0) {
        b.delay -= dt
      } else {
        b.evy += ENTRANCE_G * dt
        b.ey += b.evy * dt
        if (b.ey >= 0) {
          b.ey = 0
          b.landed = true
          b.vsq += Math.min(0.055, b.evy * 0.00006)
          b.vrot += rand(-0.8, 0.8)
          ripple(pile[i]!.x, Math.min(0.22, b.evy * 0.0002), i)
        }
      }
    }
    b.vdx += -b.dx * 0.055 * f
    b.vdy += -b.dy * 0.055 * f
    b.vrot += -b.rot * 0.05 * f
    b.vsq += -b.sq * 0.13 * f
    const damp = Math.pow(0.9, f)
    b.vdx *= damp
    b.vdy *= damp
    b.vrot *= Math.pow(0.91, f)
    b.vsq *= Math.pow(0.84, f)
    b.dx += b.vdx * f
    b.dy += b.vdy * f
    b.rot += b.vrot * f
    b.sq += b.vsq * f
  }

  if (ph === 'idle' || ph === 'carry') {
    sim.drive += (dir - sim.drive) * (1 - Math.exp(-13 * dt))
    sim.vx += sim.drive * 720 * dt
    sim.vx *= Math.exp(-5.5 * dt)
    sim.x = Math.min(CLAW_MAX, Math.max(CLAW_MIN, sim.x + sim.vx * dt))
    if (ph === 'carry') {
      sim.xrot += (sim.sway * 0.5 - sim.xrot) * (1 - Math.exp(-6 * dt))
      const { ex, ey } = pend()
      sim.carry.x = ex
      sim.carry.y = gripY(ey)
      const over = sim.x >= TRAY.min && sim.x <= TRAY.max
      if (over !== wasOverTray) {
        wasOverTray = over
        overTray.value = over
      }
    }
  } else if (ph === 'seq') {
    if (sim.stage === 'antic') {
      const p = stageP(T.antic, dt)
      sim.y = HOME_Y - ANTIC_RISE * easeOutCubic(p)
      sim.close = -0.55 * easeOutCubic(p)
      if (p >= 1) {
        const cand = candidateAt(sim.x)
        if (cand >= 0) {
          const c = toyCenter(cand)
          sim.depthY = Math.min(GH - 46, Math.max(HOME_Y + 50, c.y - GRIP_OFFSET - pile[cand]!.w / 2))
        } else {
          sim.depthY = DROP_Y
        }
        nextStage('down')
      }
    } else if (sim.stage === 'down') {
      const p = stageP(T.down, dt)
      sim.y = HOME_Y - ANTIC_RISE + (sim.depthY - HOME_Y + ANTIC_RISE) * easeInQuad(p)
      if (sim.y > 130 && !reduce) {
        pile.forEach((q, i) => {
          const d = Math.abs(q.x - sim.x)
          if (d < 56) {
            const push = (1 - d / 56) * 7 * dt
            const side = q.x < sim.x ? -1 : 1
            soft[i]!.vdx += side * push
            soft[i]!.vsq += push * 0.012
          }
        })
      }
      if (p >= 1) nextStage('dwell1')
    } else if (sim.stage === 'dwell1') {
      const p = stageP(T.dwell1, dt)
      sim.y = sim.depthY + (reduce ? 0 : 3.5 * Math.sin(Math.PI * p))
      if (p >= 1) {
        sim.y = sim.depthY
        nextStage('close')
      }
    } else if (sim.stage === 'close') {
      const p = stageP(T.close, dt)
      sim.close = -0.55 + 1.55 * easeOutCubic(p)
      if (p >= 1) {
        const best = candidateAt(sim.x)
        sim.carried = best
        carriedRef.value = best
        if (best >= 0) {
          sim.carry = { ...toyCenter(best) }
          sim.xrot = pile[best]!.rot + soft[best]!.rot
          const el = pileEls[best]
          if (el) el.style.visibility = 'hidden'
          ripple(pile[best]!.x, 0.35, best)
          if (carriedEl.value) {
            carriedEl.value.src = el?.src ?? carriedEl.value.src
            carriedEl.value.style.width = `${pile[best]!.w}px`
            carriedEl.value.style.visibility = 'visible'
            carriedEl.value.style.opacity = ''
          }
        } else {
          ripple(sim.x, 0.2)
        }
        nextStage('dwell2')
      }
    } else if (sim.stage === 'dwell2') {
      if (stageP(T.dwell2, dt) >= 1) nextStage(sim.carried >= 0 && !reduce ? 'load' : 'up')
      if (sim.carried >= 0) {
        sim.xrot += -sim.xrot * (1 - Math.exp(-3.5 * dt))
        const { ex, ey } = pend()
        sim.carry.x = ex
        sim.carry.y = gripY(ey)
      }
    } else if (sim.stage === 'load') {
      const p = stageP(T.load, dt)
      const bell = Math.sin(Math.PI * p)
      sim.y = sim.depthY + 6 * bell
      sim.close = 1 + 0.12 * bell
      sim.xrot += -sim.xrot * (1 - Math.exp(-3.5 * dt))
      const { ex, ey } = pend()
      sim.carry.x = ex
      sim.carry.y = gripY(ey)
      if (p >= 1) {
        sim.y = sim.depthY
        nextStage('up')
      }
    } else if (sim.stage === 'up') {
      const p = stageP(T.up, dt)
      sim.y = sim.depthY + (HOME_Y - sim.depthY) * easeInOutCubic(p)
      if (sim.carried >= 0) {
        sim.xrot += -sim.xrot * (1 - Math.exp(-3.5 * dt))
        const { ex, ey } = pend()
        sim.carry.x = ex
        sim.carry.y = gripY(ey)
      }
      if (p >= 1) {
        if (sim.carried >= 0) {
          setPhaseBoth('carry')
          message.value = null
        } else {
          setPhaseBoth('idle')
          message.value = '抓空了，再试一次'
        }
      }
    }
  } else if (ph === 'toTray') {
    const right = sim.carried === targetIdx
    if (sim.stage === 'open') {
      const p = stageP(T.open, dt)
      sim.close = 1 - easeOutCubic(p)
      if (sim.st > 0.12 && !sim.released) {
        sim.released = true
        if (right) {
          trayMode.value = 'open'
          if (reduce) {
            if (carriedEl.value) carriedEl.value.style.visibility = 'hidden'
            sim.carried = -1
            carriedRef.value = -1
            overTray.value = false
            trayMode.value = 'win'
            nextStage('beat')
            setPhaseBoth('celebrate')
          } else {
            const m = machineEl.value?.getBoundingClientRect()
            const tr = trayEl.value?.getBoundingClientRect()
            if (m && tr) sim.mouthY = tr.top - m.top + 2
            sim.fallV = 30
          }
        } else {
          sim.fallV = 40
        }
      }
    }
    if (right && sim.released && sim.carried >= 0) {
      sim.fallV = Math.min(sim.fallV + DROP_G * dt, 460)
      sim.carry.y += sim.fallV * dt
      sim.carry.x += (TRAY.cx - sim.carry.x) * (1 - Math.exp(-2.2 * dt))
      sim.xrot += -sim.xrot * (1 - Math.exp(-4 * dt))
      sim.stretch = (Math.abs(sim.fallV) / 460) * 0.09
      const w = pile[sim.carried]!.w
      const sunk = sim.carry.y + w / 2 - sim.mouthY
      if (sunk > 0 && carriedEl.value) {
        carriedEl.value.style.clipPath = `inset(0 0 ${sunk.toFixed(1)}px 0)`
        carriedEl.value.style.filter = `brightness(${Math.max(0.4, 1 - (sunk / w) * 0.75).toFixed(3)})`
      }
      if (sunk >= w + 4) {
        if (carriedEl.value) {
          carriedEl.value.style.visibility = 'hidden'
          carriedEl.value.style.clipPath = ''
          carriedEl.value.style.filter = ''
        }
        sim.carried = -1
        carriedRef.value = -1
        overTray.value = false
        trayMode.value = 'win'
        nextStage('beat')
        setPhaseBoth('celebrate')
      }
    }
    if (!right && sim.fallV !== 0) {
      sim.fallV = Math.min(sim.fallV + DROP_G * dt, 360)
      sim.carry.y += sim.fallV * dt
      sim.carry.x += (TRAY.cx - sim.carry.x) * (1 - Math.exp(-4 * dt))
      sim.xrot += -sim.xrot * (1 - Math.exp(-3 * dt))
      sim.stretch = (Math.abs(sim.fallV) / 460) * 0.09
      if (sim.fallV > 0 && sim.carry.y >= TRAY.cy) {
        if (sim.fallV > 200) {
          sim.carry.y = TRAY.cy
          sim.fallV = -sim.fallV * 0.28
          sim.xrot += rand(-7, 7)
        } else {
          sim.carry.y = TRAY.cy
          sim.fallV = 0
          sim.stretch = 0
          overTray.value = false
          trayMode.value = 'no'
          message.value = `这是${TOY_META[pile[sim.carried]!.toy].label}！要找的是${targetMeta.label}`
          nextStage('beat')
          setPhaseBoth('deny')
        }
      }
    }
  } else if (ph === 'celebrate') {
    if (sim.stage === 'beat') {
      if (stageP(0.28, dt) >= 1) {
        nextStage('shine')
        verified.value = true
        emit('verify')
      }
    } else if (sim.stage === 'shine') {
      if (stageP(0.7, dt) >= 1) setPhaseBoth('done')
    }
  } else if (ph === 'deny') {
    if (sim.stage === 'beat') {
      const p = stageP(0.46, dt)
      sim.swallow = easeOutCubic(p)
      sim.carry.y -= 46 * dt
      if (p >= 1) {
        const idx = sim.carried
        trayMode.value = ''
        const el = pileEls[idx]
        if (el) el.style.visibility = ''
        if (carriedEl.value) {
          carriedEl.value.style.visibility = 'hidden'
          carriedEl.value.style.opacity = ''
          carriedEl.value.style.clipPath = ''
          carriedEl.value.style.filter = ''
        }
        sim.swallow = 0
        const b = soft[idx]!
        b.vsq += 0.05
        b.vrot += rand(-1, 1)
        ripple(pile[idx]!.x, 0.25, idx)
        sim.carried = -1
        carriedRef.value = -1
        setPhaseBoth('idle')
      }
    }
  }

  render()
  raf = requestAnimationFrame(step)
}

let prevNow = 0
let wasOverTray = false

onMounted(() => {
  if (reduce) {
    soft.forEach((b) => {
      b.ey = 0
      b.landed = true
    })
  }
  render()
  raf = requestAnimationFrame(step)
})
onBeforeUnmount(() => cancelAnimationFrame(raf))

/* ---------------- 交互 ---------------- */
function action() {
  if (verified.value) return
  if (phase.value === 'idle') {
    message.value = null
    sim.close = 0
    sim.stage = 'antic'
    sim.st = 0
    setPhaseBoth('seq')
  } else if (phase.value === 'carry') {
    if (sim.x >= TRAY.min && sim.x <= TRAY.max) {
      if (carriedEl.value) {
        carriedEl.value.style.visibility = 'visible'
        carriedEl.value.style.opacity = ''
        carriedEl.value.style.clipPath = ''
        carriedEl.value.style.filter = ''
      }
      sim.stage = 'open'
      sim.st = 0
      sim.fallV = 0
      sim.swallow = 0
      sim.stretch = 0
      sim.released = false
      overTray.value = false
      setPhaseBoth('toTray')
    } else {
      message.value = '先把玩具移到投放区上方'
    }
  }
}

let stickDrag: { id: number; startX: number } | null = null
function onStickDown(e: PointerEvent) {
  if (verified.value) return
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  stickDrag = { id: e.pointerId, startX: e.clientX }
  if (stickEl.value) stickEl.value.style.transition = 'none'
}
function onStickMove(e: PointerEvent) {
  const d = stickDrag
  if (!d || e.pointerId !== d.id) return
  const dx = Math.max(-26, Math.min(26, e.clientX - d.startX))
  dir = dx / 26
  if (stickEl.value) stickEl.value.style.transform = `rotate(${(dx * 1.05).toFixed(1)}deg)`
}
function onStickUp(e: PointerEvent) {
  if (stickDrag?.id !== e.pointerId) return
  stickDrag = null
  dir = 0
  if (stickEl.value) {
    stickEl.value.style.transition = 'transform 0.25s cubic-bezier(0.2, 1.6, 0.4, 1)'
    stickEl.value.style.transform = ''
  }
}

function onKeyDown(e: KeyboardEvent) {
  if (infoOpen.value) {
    if (e.key === 'Escape') infoOpen.value = false
    return
  }
  if (verified.value) return
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    dir = -1
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    dir = 1
  } else if ((e.key === ' ' || e.key === 'Enter') && !e.repeat) {
    e.preventDefault()
    action()
  }
}
function onKeyUp(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') dir = 0
}
</script>

<style src="./clawcaptcha.css" scoped></style>

<style scoped>
/* ---- 移植补充：用 CSS 过渡替代原 React 版的 motion 装饰动画 ---- */
.clawcap { animation: ccIn 0.55s cubic-bezier(0.2, 0.8, 0.2, 1); }
@keyframes ccIn { from { opacity: 0; transform: translateY(16px) scale(0.985); } }
.clawcap-shield--ok { animation: ccPop 0.45s cubic-bezier(0.34, 1.56, 0.64, 1); }
@keyframes ccPop { from { transform: scale(0.55); } }
.cc-xfspan { display: inline-block; }
.ccxf-enter-active, .ccxf-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.ccxf-enter-from { opacity: 0; transform: translateY(4px); }
.ccxf-leave-to { opacity: 0; transform: translateY(-4px); }
.ccfade-enter-active, .ccfade-leave-active { transition: opacity 0.18s ease; }
.ccfade-enter-from, .ccfade-leave-to { opacity: 0; }
@media (prefers-reduced-motion: reduce) {
  .clawcap, .clawcap-shield--ok { animation: none; }
}
</style>

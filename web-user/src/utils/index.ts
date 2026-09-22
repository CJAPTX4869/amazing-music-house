// 格式化数字
export function formateNumber(num:number) {
  // 小于999的数字直接返回
  if (num < 99999) return num.toString();
  // 大于1万 小于1亿
  if (num < 100000000) return Math.round(num / 10000) + '万';
  // 大于1亿
  if (num >= 100000000) return Math.round(num / 100000000) + '亿';
 
  return num.toString();
}
// 获取数组最后一位
export const getArrLast = (arr:any[]) => {
  return arr[arr.length - 1];
};

// 节流（修复：原实现不接收调用方实参，导致 fn(...rest) 恒为空参 —— 搜索建议因此 100% 失效）
export const throttle = (fn:Function, delay:number) => {
  let timer:any = null;
  return function (...args:any[]) {
    if (timer) return;
    timer = setTimeout(() => {
      fn(...args);
      timer = null;
    }, delay);
  };
};
// 防抖（修复：原实现用 fn.apply(rest) 把实参数组当成 this 传，实参全部丢失）
export const debounce = (fn:Function, delay:number) => {
  let timer:any = null;
  return function (...rest: any) {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      fn(...rest);
      timer = null;
    }, delay);
  };
};
// 根据不同类型记忆函数
export const memorize = (fn:Function) => {
  const cache = new Map();
  return (...args:any[]) => {
    const key = JSON.stringify(args);
    if (cache.has(key)) {
      return cache.get(key);
    }
    const result = fn(...args);
    cache.set(key, result);
    return result;
  };
};
export const formateSongsAuthor = (attr: any[]) => {
  return attr.map(item => item.name).join(' / ');
};
// 根据指定的数量将数组切片
// 修复：原循环用 index++ 与 nextSliceIndex 双计数，10 个元素按 20 切片会返回 10 片（9 片为空）
export const sliceArr = (count=20, list:any[]) => {
  const arr = [];
  if (!Array.isArray(list) || count <= 0) return arr;
  let nextSliceIndex = 0;
  while (nextSliceIndex < list.length) {
    const item = list.slice(nextSliceIndex, nextSliceIndex + count);
    arr.push(item);
    nextSliceIndex += count;
  }
  return arr;
};

export const generalTimeOptions = (
  start:number, end:number, label:string
) => {
  const arr = [];
  for (let i = start; i <= end; i++) {
    arr.push({
      label: i+label,
      value: i
    });
  }
  return arr;
};
export const getDayOptions = (month:number, year:number=new Date().getFullYear()) => {
  let day;
  if ([
    1, 3, 5, 7, 8, 10, 12
  ].includes(month)) {
    day = 31;
  }
  if ([
    4, 6, 9, 11
  ].includes(month)) {
    day = 30;
  }
  // 如果此时为闰年 并且是2月（修复：原判断 year % 4 === 0 未排除整百年，2100 年 2 月会给 29 天）
  if (month === 2) {
    const isLeap = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
    day = isLeap
      ? 29
      : 28;
  }
  return generalTimeOptions(
    1, day as number, '日'
  );
}; 
// 对比两个对象是否相等
export const compareObject = (obj1:any, obj2:any) => {
  const keys1 = Object.keys(obj1);
  const keys2 = Object.keys(obj2);
  if (keys1.length !== keys2.length) return false;
  for (const key of keys1) {
    if (obj1[key] !== obj2[key]) return false;
  }
  return true;
};
export function getImgSize(file: File):Promise<{width:number, height:number}> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = function (theFile) {
      const image = new Image();
      if (theFile.target) {
        image.src = theFile.target.result as string;
        image.onload = function () {
          resolve({
            width: image.width,
            height: image.height
          });
        };
      }
      
    };
  });
}
//记录下标所对应的生成随机下标
const cacheRandomNumMap = new Map();
// 得到一个两数之间的随机整数，包括两个数在内, 不包括指定下标
// 并对指定下标生成的随机下标进行缓存
export function getRandomIntInclusive(
  min:number, max:number, index:number
) {
  if (cacheRandomNumMap.has(index)) {
    return cacheRandomNumMap.get(index);
  }
  min = Math.ceil(min);
  max = Math.floor(max);
  let randomIndex = Math.floor(Math.random() * (max - min + 1)) + min; //含最大值，含最小值 
  while (randomIndex === index) {
    randomIndex = Math.floor(Math.random() * (max - min + 1)) + min;
  }
  cacheRandomNumMap.set(index, randomIndex);
  return randomIndex;
}
// 重置
export function restRandomNumMap() {
  cacheRandomNumMap.clear();
}
// 当值等于最大值时,返回0,否则+1
export function getNextIndex(index:number, max:number) {
  return index === max
    ? 0
    : index + 1;
}
// 当值等于0时,返回最大值,否则-1
export function getPrevIndex(index:number, max:number) {
  return index === 0
    ? max
    : index - 1;
}
// 是否为偶数
export function isEven(num:number) {
  return num % 2 === 0;
}
// 对象是否为空
export function isEmptyObject(obj:any) {
  return Object.keys(obj).length === 0;
}
// sleep function with promise
export function sleep(ms:number) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

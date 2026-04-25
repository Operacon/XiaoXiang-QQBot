<div align="center">

# 潇小湘

_ 基于 [NapCat](https://github.com/NapNeko/NapCatQQ) 和 [NapCat4j](https://github.com/Operacon/NapCat4j) 的 QQ 机器人 _

</div>

---

## 重构与新生

从 0.8-DEBUG 版本开始

1. 潇小湘的服务底层从 [Mirai](https://github.com/mamoe/mirai) 更换为 [NapCat](https://github.com/NapNeko/NapCatQQ)
   。我们在挫折中共同度过了难忘而欢乐的时光，而今迈步从头越
2. 实例从原本的插件形式更换为独立的 Springboot 服务，增强了外部扩展能力
3. 潇小湘使用更新的技术栈版本：`Kotlin 2.3.20`, `Java 25`, `Springboot 4.0.5`
4. 适配层依赖基于 [Shiro](https://github.com/MisakaTAT/Shiro) 但更符合小湘旧风格的 NapCat Java
   SDK [NapCat4j](https://github.com/Operacon/NapCat4j)

潇小湘将在一段时间的重构中找回曾经的功能，顺便寻找新的乐趣~

## 快速开始

TODO

## 已开发的功能

### 群聊

#### hello 测试
发送 `小湘` 以及 `功能` `介绍` `介绍一下` `自我介绍` `help` 会收到 bot 回复。用于测试 bot 在线情况。

## 相关仓库与致谢

- [NapCat](https://github.com/NapNeko/NapCatQQ) 提供底层 QQ 服务

- [NapCat4j](https://github.com/Operacon/NapCat4j) 提供 Springboot starter/SDK

- [Mirai](https://github.com/mamoe/mirai)

---

## 开源许可

潇小湘依旧以 `AGPLv3` 协议进行开源

```
XiaoXiang, may not be an annoying bot
Copyright (C) 2026 Operacon.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

To contact the author, E-Mail <operacon@outlook.com>
```
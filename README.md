# Armor Guard

为 Minecraft Java **26.3 / Fabric** 制作的纯客户端小模组，当盔甲剩余耐久低于20时（可配置），将其移回背包。

不处理鞘翅，仅在生存模式下运行。遵守绑定诅咒

## 依赖

- Fabric API
- Cloth Config

本模组不额外限制 Fabric Loader 版本；Loader 仍需支持 Minecraft 26.3，并满足 Fabric API、Cloth Config 等前置的版本要求。

## 命令

- `/armorguard config`：打开配置界面
- `/armorguard reload`：重新加载配置文件

## 配置文件

配置文件位于 `config/armor-guard.json`

```json
{
  "enabled": true,
  "threshold": 20
}
```

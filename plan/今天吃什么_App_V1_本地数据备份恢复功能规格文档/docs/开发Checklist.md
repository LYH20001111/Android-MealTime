# 本地备份与恢复开发 Checklist

## 图片
- [ ] 图片从系统 URI 复制到 App 私有目录
- [ ] Recipe 使用 imagePath
- [ ] imagePath 使用相对路径
- [ ] UUID 作为图片文件名
- [ ] 导出时压缩为 WebP

## 备份
- [ ] data.json
- [ ] manifest.json
- [ ] images/
- [ ] ZIP
- [ ] backupVersion
- [ ] databaseVersion
- [ ] 完整性检查

## 恢复
- [ ] SAF 文件选择
- [ ] manifest 校验
- [ ] JSON 校验
- [ ] 路径安全检查
- [ ] 临时目录
- [ ] Room Transaction
- [ ] 图片恢复
- [ ] 恢复后路径校验

## UI
- [ ] 数据备份
- [ ] 恢复数据
- [ ] 恢复预览
- [ ] 备份进度
- [ ] 恢复进度
- [ ] 成功/失败状态

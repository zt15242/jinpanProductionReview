#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
飞书通知脚本
"""

import os
import json
import requests
import sys

def send_feishu_notification():
    """发送飞书通知"""
    webhook_url = os.getenv('FEISHU_WEBHOOK_URL')
    if not webhook_url:
        print("❌ 未设置FEISHU_WEBHOOK_URL环境变量")
        return False
    
    # 读取审查结果
    try:
        with open('code_review_result.md', 'r', encoding='utf-8') as f:
            review_content = f.read()
    except FileNotFoundError:
        print("❌ 未找到审查结果文件")
        return False
    
    # 获取提交信息
    commit_hash = os.getenv('COMMIT_HASH', 'unknown')[:8]
    commit_hash_full = os.getenv('COMMIT_HASH', 'unknown')
    repo_name = os.getenv('REPO_NAME', 'unknown')
    run_id = os.getenv('GITHUB_RUN_ID', '')
    commit_url = f"https://github.com/{repo_name}/commit/{commit_hash_full}"
    
    # 构建 Artifact 下载链接
    if run_id:
        artifact_url = f"https://github.com/{repo_name}/actions/runs/{run_id}"
    else:
        artifact_url = f"https://github.com/{repo_name}/actions"
    
    # 智能判断是否有问题
    has_issues = False
    issue_keywords = ['问题', 'bug', 'Bug', 'BUG', '错误', '风险', '漏洞', '建议', '优化', '改进', '注意', 
                     '需要', '应该', '可以', '警告', 'warning', 'error', 'issue', 'fix', 'todo']
    
    # 检查内容中是否包含问题关键词
    review_lower = review_content.lower()
    for keyword in issue_keywords:
        if keyword.lower() in review_lower:
            has_issues = True
            break
    
    # 根据是否有问题选择不同的标题和颜色
    if has_issues:
        title = "⚠️ 代码审查发现需要关注的问题"
        color = "orange"
        summary_text = "AI代码审查发现一些需要关注的问题或改进建议，请查看详细报告。"
    else:
        title = "✅ 代码审查通过 - 代码质量良好"
        color = "green"
        summary_text = "AI代码审查已完成，未发现明显问题，代码质量良好！"
    
    # 提取总体评估部分
    summary_start = review_content.find('## 📊 总体评估')
    summary_end = review_content.find('---', summary_start)
    if summary_start != -1 and summary_end != -1:
        summary_section = review_content[summary_start:summary_end].strip()
        # 移除标题行
        summary_lines = summary_section.split('\n')[2:]  # 跳过标题和空行
        extracted_summary = '\n'.join(summary_lines).strip()
        if extracted_summary:
            summary_text = extracted_summary[:500]  # 限制长度
    
    # 截取前800字符作为预览
    preview_content = review_content[:800]
    if len(review_content) > 800:
        preview_content += "...\n\n[完整报告请查看CI构建产物]"
    
    # 构建飞书消息
    message = {
        "msg_type": "interactive",
        "card": {
            "config": {
                "wide_screen_mode": True
            },
            "header": {
                "title": {
                    "tag": "plain_text",
                    "content": title
                },
                "template": color
            },
            "elements": [
                {
                    "tag": "div",
                    "text": {
                        "tag": "lark_md",
                        "content": f"**仓库**: `{repo_name}`\n**提交**: `{commit_hash}`\n**状态**: {summary_text}"
                    }
                },
                {
                    "tag": "hr"
                },
                {
                    "tag": "div",
                    "text": {
                        "tag": "lark_md",
                        "content": f"**📋 审查摘要预览**\n\n{preview_content}"
                    }
                },
                {
                    "tag": "hr"
                },
                {
                    "tag": "action",
                    "actions": [
                        {
                            "tag": "button",
                            "text": {
                                "tag": "plain_text",
                                "content": "查看提交"
                            },
                            "type": "primary",
                            "url": commit_url
                        },
                        {
                            "tag": "button",
                            "text": {
                                "tag": "plain_text",
                                "content": "📥 下载完整报告"
                            },
                            "type": "default",
                            "url": artifact_url
                        }
                    ]
                },
                {
                    "tag": "note",
                    "elements": [
                        {
                            "tag": "plain_text",
                            "content": "💡 由智谱AI自动生成 | 详细报告已保存到CI构建产物"
                        }
                    ]
                }
            ]
        }
    }
    
    try:
        print(f"📤 发送飞书通知...")
        print(f"  - 状态: {'发现问题' if has_issues else '代码良好'}")
        print(f"  - 颜色: {color}")
        
        response = requests.post(
            webhook_url,
            headers={'Content-Type': 'application/json'},
            data=json.dumps(message),
            timeout=30
        )
        
        if response.status_code == 200:
            print("✅ 飞书通知发送成功")
            return True
        else:
            print(f"❌ 飞书通知发送失败: {response.status_code}")
            print(f"响应: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ 飞书通知发送异常: {str(e)}")
        return False

if __name__ == "__main__":
    success = send_feishu_notification()
    sys.exit(0 if success else 1)

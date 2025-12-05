#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AI Code Review Script using Zhipu AI
Author: AI Assistant
Date: 2025-12-03
"""

import os
import sys
import json
import requests
import time

print("="*60)
print("🤖 AI代码审查脚本启动")
print("="*60)

# 检查环境变量
def check_env_vars():
    """检查必要的环境变量"""
    print("🔍 检查环境变量...")
    
    required_vars = ['ZHIPU_API_KEY', 'GITHUB_TOKEN', 'PR_NUMBER', 'REPO_NAME']
    missing_vars = []
    
    for var in required_vars:
        value = os.getenv(var)
        if value:
            if var == 'ZHIPU_API_KEY' or var == 'GITHUB_TOKEN':
                print(f"  ✅ {var}: {'已设置' + '*' * 8}")
            else:
                print(f"  ✅ {var}: {value}")
        else:
            print(f"  ❌ {var}: 未设置")
            missing_vars.append(var)
    
    if missing_vars:
        print(f"\n❌ 缺少必要环境变量: {', '.join(missing_vars)}")
        return False
    
    # 验证PR号
    pr_number = os.getenv('PR_NUMBER')
    if not pr_number or not pr_number.strip():
        print("\n❌ PR_NUMBER为空或无效")
        return False
    
    print("\n✅ 所有环境变量检查通过")
    return True

# 检查依赖
try:
    from zhipuai import ZhipuAI
    ZHIPU_SDK_AVAILABLE = True
    print("✅ zhipuai SDK 可用")
except ImportError as e:
    print(f"⚠️  zhipuai SDK 不可用: {e}")
    print("📡 切换到 HTTP API 模式")
    ZHIPU_SDK_AVAILABLE = False
    from urllib.parse import urljoin

class GitHubCodeReviewer:
    def __init__(self):
        print("\n🔧 初始化GitHubCodeReviewer...")
        
        self.pr_number = os.getenv('PR_NUMBER', '').strip()
        self.repo_name = os.getenv('REPO_NAME', '').strip()
        self.zhipu_api_key = os.getenv('ZHIPU_API_KEY')
        self.github_token = os.getenv('GITHUB_TOKEN')
        
        if not self.pr_number:
            raise ValueError("PR_NUMBER环境变量为空或未设置")
        if not self.repo_name:
            raise ValueError("REPO_NAME环境变量为空或未设置")
        
        print(f"📊 配置信息:")
        print(f"  - PR号: #{self.pr_number}")
        print(f"  - 仓库: {self.repo_name}")
        
        if ZHIPU_SDK_AVAILABLE:
            self.client = ZhipuAI(api_key=self.zhipu_api_key)
            print("  - AI模式: zhipuai SDK")
        else:
            self.zhipu_api_base = "https://open.bigmodel.cn/api/paas/v4"
            self.zhipu_headers = {
                'Authorization': f'Bearer {self.zhipu_api_key}',
                'Content-Type': 'application/json'
            }
            print("  - AI模式: HTTP API")
        
        self.github_api_base = "https://api.github.com"
        print("✅ 初始化完成\n")
    
    def get_pr_info(self):
        """获取PR详细信息"""
        print(f"📡 获取PR #{self.pr_number} 信息...")
        
        url = f"{self.github_api_base}/repos/{self.repo_name}/pulls/{self.pr_number}"
        headers = {
            'Authorization': f'token {self.github_token}',
            'Accept': 'application/vnd.github.v3+json',
            'User-Agent': 'AI-Code-Review-Bot'
        }
        
        try:
            response = requests.get(url, headers=headers, timeout=30)
            response.raise_for_status()
            
            pr_data = response.json()
            info = {
                'number': pr_data.get('number'),
                'title': pr_data.get('title', '无标题'),
                'author': pr_data.get('user', {}).get('login', '未知'),
                'url': pr_data.get('html_url', ''),
                'repo': self.repo_name,
                'state': pr_data.get('state', 'unknown'),
                'created_at': pr_data.get('created_at', ''),
                'updated_at': pr_data.get('updated_at', '')
            }
            
            print(f"✅ 获取PR信息成功:")
            print(f"  标题: {info['title']}")
            print(f"  作者: {info['author']}")
            print(f"  状态: {info['state']}")
            print(f"  链接: {info['url']}")
            
            return info
        except requests.exceptions.RequestException as e:
            print(f"❌ 获取PR信息失败: {str(e)}")
            if hasattr(e, 'response') and e.response:
                print(f"  状态码: {e.response.status_code}")
                if e.response.status_code == 404:
                    print(f"  ⚠️  PR #{self.pr_number} 可能不存在或没有访问权限")
                print(f"  响应: {e.response.text[:200]}")
            
            # 返回基础信息
            return {
                'number': self.pr_number,
                'title': f'PR #{self.pr_number}',
                'author': '未知',
                'url': f'https://github.com/{self.repo_name}/pull/{self.pr_number}',
                'repo': self.repo_name,
                'state': 'unknown',
                'created_at': '',
                'updated_at': ''
            }
    
    def get_changed_files(self):
        """获取PR中修改的文件列表"""
        print(f"\n📡 获取PR #{self.pr_number} 的变更文件...")
        
        url = f"{self.github_api_base}/repos/{self.repo_name}/pulls/{self.pr_number}/files"
        headers = {
            'Authorization': f'token {self.github_token}',
            'Accept': 'application/vnd.github.v3+json',
            'User-Agent': 'AI-Code-Review-Bot'
        }
        
        try:
            response = requests.get(url, headers=headers, timeout=30)
            response.raise_for_status()
            
            files = response.json()
            print(f"✅ 获取到 {len(files)} 个文件")
            
            if not files:
                print("⚠️  没有变更的文件")
                return []
            
            for file in files[:10]:  # 只显示前10个文件
                print(f"  - {file['filename']} ({file['status']}, 变更: {file.get('changes', 0)}行)")
            
            if len(files) > 10:
                print(f"  ... 还有 {len(files) - 10} 个文件")
            
            return files
        except requests.exceptions.RequestException as e:
            print(f"❌ 获取变更文件失败: {str(e)}")
            if hasattr(e, 'response') and e.response:
                print(f"  状态码: {e.response.status_code}")
                print(f"  响应: {e.response.text[:200]}")
            return []
    
    def review_code_with_ai(self, code_diff, filename):
        """使用智谱AI审查代码"""
        print(f"🤖 AI审查 {filename}...")
        
        prompt = f"""你是一位专业的代码审查专家。请仔细审查以下代码变更，并提供专业的反馈。

文件名: {filename}

代码变更:
{code_diff[:2000]} # 限制长度

text

请从以下几个方面进行审查:
1. **代码质量**: 代码是否清晰、可读、可维护
2. **潜在Bug**: 是否存在潜在的错误或边界情况未处理
3. **性能问题**: 是否存在性能瓶颈或可优化的地方
4. **安全问题**: 是否存在安全漏洞或风险
5. **最佳实践**: 是否符合编程最佳实践和代码规范
6. **改进建议**: 具体的改进建议

请以专业、建设性的方式提供反馈，如果代码质量很好，也请给予肯定。"""

        try:
            if ZHIPU_SDK_AVAILABLE:
                response = self.client.chat.completions.create(
                    model="glm-4-flash",
                    messages=[
                        {
                            "role": "user",
                            "content": prompt
                        }
                    ],
                    temperature=0.7,
                    max_tokens=1500
                )
                content = response.choices[0].message.content
            else:
                data = {
                    "model": "glm-4-flash",
                    "messages": [
                        {
                            "role": "user",
                            "content": prompt
                        }
                    ],
                    "temperature": 0.7,
                    "max_tokens": 1500
                }
                
                url = urljoin(self.zhipu_api_base, "/chat/completions")
                response = requests.post(
                    url, 
                    headers=self.zhipu_headers, 
                    json=data, 
                    timeout=60
                )
                response.raise_for_status()
                result = response.json()
                content = result['choices'][0]['message']['content']
            
            print(f"✅ {filename} 审查完成")
            return content
            
        except Exception as e:
            error_msg = f"❌ AI审查失败: {str(e)}"
            print(error_msg)
            return error_msg
    
    def generate_review_summary(self, reviews):
        """生成审查总结"""
        print("\n📝 生成审查总结...")
        
        # 如果审查结果很少，直接返回
        if len(reviews) == 0:
            return "本次没有需要审查的代码文件。"
        elif len(reviews) == 1:
            return reviews[0]['review']
        
        summary_prompt = f"""基于以下各个文件的代码审查结果，生成一个简洁的总体评估和关键建议摘要。

审查了 {len(reviews)} 个文件:
{json.dumps([{'filename': r['filename']} for r in reviews], ensure_ascii=False, indent=2)}

审查结果:
{json.dumps([{'filename': r['filename'], 'summary': r['review'][:500]} for r in reviews], ensure_ascii=False, indent=2)}

请生成:
1. 总体评估 (1-2句话)
2. 主要问题汇总 (如果有)
3. 优先级最高的3个改进建议

保持简洁专业。"""

        try:
            if ZHIPU_SDK_AVAILABLE:
                response = self.client.chat.completions.create(
                    model="glm-4-flash",
                    messages=[
                        {
                            "role": "user",
                            "content": summary_prompt
                        }
                    ],
                    temperature=0.7,
                    max_tokens=800
                )
                content = response.choices[0].message.content
            else:
                data = {
                    "model": "glm-4-flash",
                    "messages": [
                        {
                            "role": "user",
                            "content": summary_prompt
                        }
                    ],
                    temperature= 0.7,
                    max_tokens=800
                }
                
                url = urljoin(self.zhipu_api_base, "/chat/completions")
                response = requests.post(
                    url, 
                    headers=self.zhipu_headers, 
                    json=data, 
                    timeout=60
                )
                response.raise_for_status()
                result = response.json()
                content = result['choices'][0]['message']['content']
            
            print("✅ 总结生成完成")
            return content
            
        except Exception as e:
            return f"总结生成失败: {str(e)}"
    
    def format_review_markdown(self, reviews, summary, pr_info):
        """格式化审查结果为Markdown"""
        markdown = f"# 🤖 AI代码审查报告 - PR #{pr_info['number']}\n\n"
        markdown += f"**PR标题**: {pr_info['title']}\n\n"
        markdown += f"**提交人**: @{pr_info['author']}\n\n"
        markdown += f"**审查时间**: {time.strftime('%Y-%m-%d %H:%M:%S')}\n\n"
        markdown += "*由智谱AI自动生成*\n\n"
        markdown += "---\n\n"
        
        markdown += "## 📊 总体评估\n\n"
        markdown += summary + "\n\n"
        markdown += "---\n\n"
        
        if reviews:
            markdown += f"## 📁 详细审查 ({len(reviews)}个文件)\n\n"
            
            for review in reviews:
                filename = review['filename']
                content = review['review']
                
                markdown += f"### 📄 `{filename}`\n\n"
                markdown += content + "\n\n"
                markdown += "---\n\n"
        else:
            markdown += "## 📁 详细审查\n\n"
            markdown += "本次没有需要审查的代码文件。\n\n"
            markdown += "---\n\n"
        
        markdown += "\n> 💡 **提示**: 这是AI自动生成的审查意见，仅供参考。请结合实际情况和团队标准进行决策。\n"
        markdown += "> 🔧 **反馈**: 如有问题或建议，欢迎在评论中提出。\n"
        
        return markdown
    
    def run(self):
        """执行代码审查流程"""
        print("\n" + "="*60)
        print("🚀 开始AI代码审查流程")
        print("="*60)
        
        # 获取PR信息
        pr_info = self.get_pr_info()
        
        # 获取变更的文件
        changed_files = self.get_changed_files()
        
        if not changed_files:
            print("⚠️  没有找到变更的文件")
            # 创建一个简单的报告
            summary = "本次PR没有变更的文件需要审查。"
            markdown_output = self.format_review_markdown([], summary, pr_info)
            
            with open('review_result.md', 'w', encoding='utf-8') as f:
                f.write(markdown_output)
            
            print("✅ 已生成空审查报告")
            return
        
        # 过滤代码文件
        code_extensions = ['.py', '.java', '.js', '.ts', '.go', '.cpp', '.c', 
                          '.cs', '.php', '.rb', '.swift', '.kt', '.scala',
                          '.apex', '.cls', '.trigger', '.html', '.css',
                          '.json', '.yaml', '.yml', '.md', '.txt', '.xml']
        
        code_files = []
        for file_info in changed_files:
            filename = file_info['filename']
            
            # 跳过删除的文件
            if file_info['status'] == 'removed':
                print(f"⏭️  跳过已删除文件: {filename}")
                continue
            
            # 检查是否为可审查的文件
            is_code_file = any(filename.endswith(ext) for ext in code_extensions)
            if not is_code_file:
                print(f"⏭️  跳过非代码文件: {filename}")
                continue
            
            # 跳过过大的文件
            changes = file_info.get('changes', 0)
            if changes > 1000:
                print(f"⏭️  跳过大文件: {filename} (变更行数: {changes})")
                continue
            
            code_files.append(file_info)
        
        if not code_files:
            print("⚠️  没有需要审查的代码文件")
            summary = "本次PR没有需要AI审查的代码文件（可能都是非代码文件或文件过大）。"
            markdown_output = self.format_review_markdown([], summary, pr_info)
            
            with open('review_result.md', 'w', encoding='utf-8') as f:
                f.write(markdown_output)
            
            print("✅ 已生成空审查报告")
            return
        
        print(f"\n🔍 需要审查 {len(code_files)} 个代码文件")
        
        reviews = []
        
        # 对每个代码文件进行审查
        for i, file_info in enumerate(code_files):
            filename = file_info['filename']
            patch = file_info.get('patch', '')
            
            print(f"\n[{i+1}/{len(code_files)}] 审查: {filename}")
            
            if not patch:
                print(f"  ⚠️  文件无变更内容，跳过")
                continue
            
            review_result = self.review_code_with_ai(patch, filename)
            reviews.append({
                'filename': filename,
                'review': review_result
            })
            
            # 添加延迟避免请求过快
            if i < len(code_files) - 1:
                time.sleep(1)
        
        if not reviews:
            print("⚠️  没有生成审查结果")
            summary = "AI审查未生成具体结果（可能所有文件都无变更内容）。"
            markdown_output = self.format_review_markdown([], summary, pr_info)
        else:
            # 生成总结
            summary = self.generate_review_summary(reviews)
            
            # 格式化输出
            markdown_output = self.format_review_markdown(reviews, summary, pr_info)
        
        # 保存到文件
        with open('review_result.md', 'w', encoding='utf-8') as f:
            f.write(markdown_output)
        
        print("\n" + "="*60)
        print("✅ 代码审查完成！")
        print(f"📄 结果已保存到: review_result.md")
        print(f"📝 报告长度: {len(markdown_output)} 字符")
        print("="*60)
        
        # 打印报告预览
        if len(markdown_output) > 500:
            print("\n📋 报告预览（前500字符）:")
            print(markdown_output[:500] + "...")
        else:
            print("\n📋 完整报告:")
            print(markdown_output)

def main():
    """主函数"""
    try:
        # 检查环境变量
        if not check_env_vars():
            sys.exit(1)
        
        # 创建审查器并运行
        reviewer = GitHubCodeReviewer()
        reviewer.run()
        
    except Exception as e:
        print(f"\n❌ 脚本执行失败: {str(e)}")
        print("\n📋 故障排除:")
        print("1. 检查所有环境变量是否设置正确")
        print("2. 检查PR号是否存在")
        print("3. 检查GitHub Token是否有足够权限")
        print("4. 检查Zhipu API Key是否有效")
        sys.exit(1)

if __name__ == "__main__":
    main()

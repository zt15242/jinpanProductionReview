#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
提交时自动代码审查脚本
Author: AI Assistant
Date: 2025-12-03
"""

import os
import sys
import json
import requests
import time
from datetime import datetime
import subprocess

print("="*60)
print("🤖 提交时AI代码审查脚本启动")
print("="*60)

# 检查环境变量
def check_env_vars():
    """检查必要的环境变量"""
    print("🔍 检查环境变量...")
    
    required_vars = ['ZHIPU_API_KEY', 'GITHUB_TOKEN', 'COMMIT_HASH', 'REPO_NAME']
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
    
    print("\n✅ 环境变量检查通过")
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

class CommitCodeReviewer:
    def __init__(self):
        print("\n🔧 初始化CommitCodeReviewer...")
        
        self.commit_hash = os.getenv('COMMIT_HASH', '').strip()
        self.repo_name = os.getenv('REPO_NAME', '').strip()
        self.zhipu_api_key = os.getenv('ZHIPU_API_KEY')
        self.github_token = os.getenv('GITHUB_TOKEN')
        
        if not self.commit_hash:
            raise ValueError("COMMIT_HASH环境变量为空或未设置")
        if not self.repo_name:
            raise ValueError("REPO_NAME环境变量为空或未设置")
        
        print(f"📊 配置信息:")
        print(f"  - 提交哈希: {self.commit_hash[:8]}")
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
    
    def get_commit_info(self):
        """获取提交信息"""
        print(f"📡 获取提交 {self.commit_hash[:8]} 信息...")
        
        url = f"{self.github_api_base}/repos/{self.repo_name}/commits/{self.commit_hash}"
        headers = {
            'Authorization': f'token {self.github_token}',
            'Accept': 'application/vnd.github.v3+json',
            'User-Agent': 'AI-Code-Review-Bot'
        }
        
        try:
            response = requests.get(url, headers=headers, timeout=30)
            response.raise_for_status()
            
            commit_data = response.json()
            info = {
                'hash': commit_data.get('sha', ''),
                'short_hash': commit_data.get('sha', '')[:8],
                'author': commit_data.get('commit', {}).get('author', {}).get('name', '未知'),
                'author_login': commit_data.get('author', {}).get('login', '未知') if commit_data.get('author') else '未知',
                'message': commit_data.get('commit', {}).get('message', '无消息').split('\n')[0],
                'full_message': commit_data.get('commit', {}).get('message', '无消息'),
                'date': commit_data.get('commit', {}).get('author', {}).get('date', ''),
                'url': commit_data.get('html_url', ''),
                'repo': self.repo_name,
                'changed_files': len(commit_data.get('files', []))
            }
            
            print(f"✅ 获取提交信息成功:")
            print(f"  作者: {info['author']} (@{info['author_login']})")
            print(f"  消息: {info['message']}")
            print(f"  时间: {info['date']}")
            print(f"  变更文件数: {info['changed_files']}")
            print(f"  链接: {info['url']}")
            
            return info
        except requests.exceptions.RequestException as e:
            print(f"❌ 获取提交信息失败: {str(e)}")
            
            # 尝试从git命令获取信息
            try:
                print("尝试从本地git获取提交信息...")
                author = subprocess.check_output(['git', 'log', '-1', '--format=%an', self.commit_hash], 
                                                stderr=subprocess.STDOUT, text=True).strip()
                message = subprocess.check_output(['git', 'log', '-1', '--format=%s', self.commit_hash], 
                                                 stderr=subprocess.STDOUT, text=True).strip()
                date = subprocess.check_output(['git', 'log', '-1', '--format=%ad', self.commit_hash], 
                                              stderr=subprocess.STDOUT, text=True).strip()
                
                info = {
                    'hash': self.commit_hash,
                    'short_hash': self.commit_hash[:8],
                    'author': author,
                    'author_login': author,
                    'message': message,
                    'full_message': message,
                    'date': date,
                    'url': f'https://github.com/{self.repo_name}/commit/{self.commit_hash}',
                    'repo': self.repo_name,
                    'changed_files': 0
                }
                
                print(f"✅ 从本地git获取成功:")
                print(f"  作者: {info['author']}")
                print(f"  消息: {info['message']}")
                
                return info
            except Exception as git_error:
                print(f"❌ 本地git获取也失败: {str(git_error)}")
            
            # 返回基础信息
            return {
                'hash': self.commit_hash,
                'short_hash': self.commit_hash[:8],
                'author': '未知',
                'author_login': '未知',
                'message': '无法获取提交信息',
                'full_message': '无法获取提交信息',
                'date': datetime.now().isoformat(),
                'url': f'https://github.com/{self.repo_name}/commit/{self.commit_hash}',
                'repo': self.repo_name,
                'changed_files': 0
            }
    
    def get_changed_files(self):
        """获取提交中修改的文件列表"""
        print(f"\n📡 获取提交 {self.commit_hash[:8]} 的变更文件...")
        
        # 首先尝试GitHub API
        url = f"{self.github_api_base}/repos/{self.repo_name}/commits/{self.commit_hash}"
        headers = {
            'Authorization': f'token {self.github_token}',
            'Accept': 'application/vnd.github.v3+json',
            'User-Agent': 'AI-Code-Review-Bot'
        }
        
        try:
            response = requests.get(url, headers=headers, timeout=30)
            response.raise_for_status()
            
            commit_data = response.json()
            files = commit_data.get('files', [])
            
            print(f"✅ 从GitHub API获取到 {len(files)} 个文件")
            
            if not files:
                print("⚠️  没有变更的文件")
                return []
            
            for file in files[:10]:
                print(f"  - {file['filename']} ({file['status']}, 变更: {file.get('changes', 0)}行)")
            
            if len(files) > 10:
                print(f"  ... 还有 {len(files) - 10} 个文件")
            
            return files
        except requests.exceptions.RequestException as e:
            print(f"❌ GitHub API获取失败: {str(e)}")
            
            # 尝试使用git命令获取
            try:
                print("尝试使用git命令获取变更文件...")
                # 获取父提交
                parent_hash = subprocess.check_output(['git', 'rev-parse', f'{self.commit_hash}^'], 
                                                     stderr=subprocess.PIPE, text=True).strip()
                
                # 获取变更文件列表
                diff_output = subprocess.check_output(
                    ['git', 'diff', '--name-status', parent_hash, self.commit_hash],
                    stderr=subprocess.PIPE, text=True
                )
                
                files = []
                for line in diff_output.strip().split('\n'):
                    if line:
                        parts = line.split('\t')
                        if len(parts) >= 2:
                            status = parts[0]
                            filename = parts[1]
                            
                            # 获取文件变更行数
                            try:
                                diff_lines = subprocess.check_output(
                                    ['git', 'diff', '--numstat', parent_hash, self.commit_hash, '--', filename],
                                    stderr=subprocess.PIPE, text=True
                                ).strip()
                                
                                if diff_lines:
                                    additions, deletions, _ = diff_lines.split('\t')
                                    changes = int(additions) + int(deletions)
                                else:
                                    changes = 0
                            except:
                                changes = 0
                            
                            files.append({
                                'filename': filename,
                                'status': status,
                                'changes': changes
                            })
                
                print(f"✅ 从git命令获取到 {len(files)} 个文件")
                
                for file in files[:10]:
                    print(f"  - {file['filename']} ({file['status']}, 变更: {file.get('changes', 0)}行)")
                
                return files
            except Exception as git_error:
                print(f"❌ git命令获取也失败: {str(git_error)}")
                return []
    
    def get_file_content_diff(self, filename, status):
        """获取文件的完整内容和变更信息"""
        if status == 'added':
            # 新增文件，获取完整内容
            try:
                content = subprocess.check_output(
                    ['git', 'show', f'{self.commit_hash}:{filename}'],
                    stderr=subprocess.PIPE, text=True
                )
                return f"新增文件完整内容:\n{content}"
            except:
                return "新增文件（无法获取内容）"
        elif status == 'deleted':
            # 删除文件
            try:
                content = subprocess.check_output(
                    ['git', 'show', f'{self.commit_hash}^:{filename}'],
                    stderr=subprocess.PIPE, text=True
                )
                return f"删除文件，原完整内容:\n{content}"
            except:
                return "删除文件"
        else:
            # 修改文件，获取修改前后的完整内容和diff
            try:
                parent_hash = subprocess.check_output(
                    ['git', 'rev-parse', f'{self.commit_hash}^'],
                    stderr=subprocess.PIPE, text=True
                ).strip()
                
                # 获取修改前的完整内容
                try:
                    old_content = subprocess.check_output(
                        ['git', 'show', f'{parent_hash}:{filename}'],
                        stderr=subprocess.PIPE, text=True
                    )
                except:
                    old_content = "无法获取修改前内容"
                
                # 获取修改后的完整内容
                try:
                    new_content = subprocess.check_output(
                        ['git', 'show', f'{self.commit_hash}:{filename}'],
                        stderr=subprocess.PIPE, text=True
                    )
                except:
                    new_content = "无法获取修改后内容"
                
                # 获取diff信息
                try:
                    diff = subprocess.check_output(
                        ['git', 'diff', '--no-color', parent_hash, self.commit_hash, '--', filename],
                        stderr=subprocess.PIPE, text=True
                    )
                except:
                    diff = "无法获取diff"
                
                # 组合返回修改前后的完整内容和diff
                result = f"=== 文件修改概览 ===\n\n"
                result += f"变更详情 (Diff):\n{diff}\n\n"
                result += f"=== 修改后的完整文件内容 ===\n{new_content}\n\n"
                result += f"=== 修改前的完整文件内容（供参考）===\n{old_content}"
                
                return result
            except Exception as e:
                return f"文件修改（无法获取完整内容）: {str(e)}"
    
    def review_code_with_ai(self, filename, content_diff, status):
        """使用智谱AI审查代码"""
        print(f"🤖 AI审查 {filename} ({status})...")
        
        # 不限制代码长度，直接使用完整内容
        truncated_content = content_diff
        
        if status == 'modified' or status == 'M':
            prompt = f"""你是一位专业的代码审查专家。请仔细审查以下文件的修改，我提供了修改前后的完整文件内容以及diff信息，请结合完整上下文进行审查。

文件名: {filename}
变更类型: {status}

{truncated_content}

请基于完整的文件内容和修改详情，从以下几个方面进行审查:
1. **代码质量**: 代码是否清晰、可读、可维护，修改是否与整体代码风格一致
2. **潜在Bug**: 是否存在潜在的错误或边界情况未处理，修改是否引入新的问题
3. **性能问题**: 是否存在性能瓶颈或可优化的地方
4. **安全问题**: 是否存在安全漏洞或风险
5. **最佳实践**: 是否符合编程最佳实践和代码规范
6. **上下文一致性**: 修改是否与文件其他部分保持一致
7. **改进建议**: 具体的改进建议

请以专业、建设性的方式提供反馈，如果代码质量很好，也请给予肯定。"""
        else:
            prompt = f"""你是一位专业的代码审查专家。请仔细审查以下代码，并提供专业的反馈。

文件名: {filename}
变更类型: {status}

代码内容:
{truncated_content}

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
                    model="glm-4-air",
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
                    "model": "glm-4-air",
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
    
    def generate_review_summary(self, reviews, commit_info):
        """生成审查总结"""
        print("\n📝 生成审查总结...")
        
        if len(reviews) == 0:
            return "本次提交没有需要审查的代码文件。"
        
        summary_prompt = f"""基于以下提交的代码审查结果，生成一个简洁的总体评估和关键建议摘要。

提交信息:
- 提交者: {commit_info['author']}
- 提交消息: {commit_info['message']}
- 变更文件数: {len(reviews)}个

审查了 {len(reviews)} 个文件:
{json.dumps([{'filename': r['filename'], 'status': r['status']} for r in reviews], ensure_ascii=False, indent=2)}

审查结果摘要:
{json.dumps([{'filename': r['filename'], 'summary': r['review'][:300]} for r in reviews], ensure_ascii=False, indent=2)}

请生成:
1. 总体评估 (1-2句话)
2. 主要问题汇总 (如果有)
3. 优先级最高的3个改进建议

保持简洁专业。"""

        try:
            if ZHIPU_SDK_AVAILABLE:
                response = self.client.chat.completions.create(
                    model="glm-4-air",
                    messages=[
                        {
                            "role": "user",
                            "content": summary_prompt
                        }
                    ],
                    temperature=0.7,
                    max_tokens=1000
                )
                content = response.choices[0].message.content
            else:
                data = {
                    "model": "glm-4-air",
                    "messages": [
                        {
                            "role": "user",
                            "content": summary_prompt
                        }
                    ],
                    "temperature": 0.7,
                    "max_tokens": 1000
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
    
    def format_review_markdown(self, reviews, summary, commit_info):
        """格式化审查结果为Markdown"""
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        
        markdown = f"# 🤖 AI代码审查报告\n\n"
        markdown += f"**提交哈希**: `{commit_info['short_hash']}`\n\n"
        markdown += f"**提交者**: {commit_info['author']} (@{commit_info['author_login']})\n\n"
        markdown += f"**提交消息**: {commit_info['message']}\n\n"
        markdown += f"**提交时间**: {commit_info['date']}\n\n"
        markdown += f"**审查时间**: {timestamp}\n\n"
        markdown += f"**提交链接**: [查看提交]({commit_info['url']})\n\n"
        markdown += "*由智谱AI自动生成*\n\n"
        markdown += "---\n\n"
        
        markdown += "## 📊 总体评估\n\n"
        markdown += summary + "\n\n"
        markdown += "---\n\n"
        
        if reviews:
            markdown += f"## 📁 详细审查 ({len(reviews)}个文件)\n\n"
            
            for review in reviews:
                filename = review['filename']
                status = review['status']
                content = review['review']
                
                status_emoji = {
                    'added': '🆕',
                    'modified': '📝',
                    'deleted': '🗑️',
                    'A': '🆕',
                    'M': '📝',
                    'D': '🗑️'
                }.get(status, '📄')
                
                markdown += f"### {status_emoji} `{filename}` ({status})\n\n"
                markdown += content + "\n\n"
                markdown += "---\n\n"
        else:
            markdown += "## 📁 详细审查\n\n"
            markdown += "本次提交没有需要审查的代码文件。\n\n"
            markdown += "---\n\n"
        
        markdown += "\n> 💡 **提示**: 这是AI自动生成的审查意见，仅供参考。请结合实际情况和团队标准进行决策。\n"
        
        return markdown
    
    def run(self):
        """执行代码审查流程"""
        print("\n" + "="*60)
        print("🚀 开始提交时AI代码审查流程")
        print("="*60)
        
        # 获取提交信息
        commit_info = self.get_commit_info()
        
        # 获取变更的文件
        changed_files = self.get_changed_files()
        
        if not changed_files:
            print("⚠️  没有找到变更的文件")
            summary = "本次提交没有变更的文件需要审查。"
            markdown_output = self.format_review_markdown([], summary, commit_info)
            
            with open(f'code_review_{commit_info["short_hash"]}.md', 'w', encoding='utf-8') as f:
                f.write(markdown_output)
            
            print("✅ 已生成空审查报告")
            return
        
        # 过滤代码文件
        code_extensions = ['.py', '.java', '.js', '.ts', '.go', '.cpp', '.c', 
                          '.cs', '.php', '.rb', '.swift', '.kt', '.scala',
                          '.apex', '.cls', '.trigger', '.html', '.css',
                          '.json', '.yaml', '.yml', '.xml', '.sql',
                          '.sh', '.bash', '.ps1', '.bat']
        
        code_files = []
        for file_info in changed_files:
            filename = file_info['filename']
            status = file_info['status']
            
            # 检查是否为可审查的文件
            is_code_file = any(filename.endswith(ext) for ext in code_extensions)
            if not is_code_file:
                print(f"⏭️  跳过非代码文件: {filename}")
                continue
            
            # 不限制文件大小，审查所有代码文件
            
            code_files.append(file_info)
        
        if not code_files:
            print("⚠️  没有需要审查的代码文件")
            summary = "本次提交没有需要AI审查的代码文件（可能都是非代码文件或文件过大）。"
            markdown_output = self.format_review_markdown([], summary, commit_info)
            
            with open(f'code_review_{commit_info["short_hash"]}.md', 'w', encoding='utf-8') as f:
                f.write(markdown_output)
            
            print("✅ 已生成空审查报告")
            return
        
        print(f"\n🔍 需要审查 {len(code_files)} 个代码文件")
        
        reviews = []
        
        # 对每个代码文件进行审查
        for i, file_info in enumerate(code_files):
            filename = file_info['filename']
            status = file_info['status']
            
            print(f"\n[{i+1}/{len(code_files)}] 审查: {filename} ({status})")
            
            # 获取文件变更内容
            content_diff = self.get_file_content_diff(filename, status)
            
            if not content_diff or content_diff.strip() == "":
                print(f"  ⚠️  文件无变更内容，跳过")
                continue
            
            review_result = self.review_code_with_ai(filename, content_diff, status)
            reviews.append({
                'filename': filename,
                'status': status,
                'review': review_result
            })
            
            # 添加延迟避免请求过快
            if i < len(code_files) - 1:
                time.sleep(1)
        
        if not reviews:
            print("⚠️  没有生成审查结果")
            summary = "AI审查未生成具体结果（可能所有文件都无变更内容）。"
            markdown_output = self.format_review_markdown([], summary, commit_info)
        else:
            # 生成总结
            summary = self.generate_review_summary(reviews, commit_info)
            
            # 格式化输出
            markdown_output = self.format_review_markdown(reviews, summary, commit_info)
        
        # 保存到文件
        output_filename = f'code_review_{commit_info["short_hash"]}.md'
        with open(output_filename, 'w', encoding='utf-8') as f:
            f.write(markdown_output)
        
        # 也保存一个通用名称的文件
        with open('code_review_result.md', 'w', encoding='utf-8') as f:
            f.write(markdown_output)
        
        print("\n" + "="*60)
        print("✅ 代码审查完成！")
        print(f"📄 结果已保存到: {output_filename}")
        print(f"📄 同时保存为: code_review_result.md")
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
        reviewer = CommitCodeReviewer()
        reviewer.run()
        
    except Exception as e:
        print(f"\n❌ 脚本执行失败: {str(e)}")
        print("\n📋 故障排除:")
        print("1. 检查所有环境变量是否设置正确")
        print("2. 检查GitHub Token是否有足够权限")
        print("3. 检查Zhipu API Key是否有效")
        sys.exit(1)

if __name__ == "__main__":
    main()

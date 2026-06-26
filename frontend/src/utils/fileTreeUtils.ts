import type { CodeFile } from '@/types/codegen'

export interface FileTreeNode {
  name: string
  path: string
  isFile: boolean
  children?: FileTreeNode[]
}

function sortNodes(nodes: FileTreeNode[]): FileTreeNode[] {
  return nodes
    .map(node => ({
      ...node,
      children: node.children ? sortNodes(node.children) : undefined,
    }))
    .sort((a, b) => {
      if (a.isFile !== b.isFile) return a.isFile ? 1 : -1
      return a.name.localeCompare(b.name)
    })
}

export function buildFileTree(files: CodeFile[] | null | undefined): FileTreeNode[] {
  const root: FileTreeNode[] = []
  if (!files?.length) return root

  for (const file of files) {
    const parts = file.path.replace(/\\/g, '/').split('/').filter(Boolean)
    let current = root
    let currentPath = ''

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      const isFile = i === parts.length - 1
      currentPath = currentPath ? `${currentPath}/${part}` : part

      let node = current.find(item => item.name === part)
      if (!node) {
        node = {
          name: part,
          path: isFile ? file.path : currentPath,
          isFile,
          children: isFile ? undefined : [],
        }
        current.push(node)
      }

      if (!isFile && node.children) {
        current = node.children
      }
    }
  }

  return sortNodes(root)
}

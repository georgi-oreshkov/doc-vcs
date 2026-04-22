import { useState } from 'react';
import { Button, Spinner, Textarea } from "@heroui/react";
import { MessageSquare, Send } from 'lucide-react';
import { useComments, useAddComment } from '../hooks/useVersions';

function formatDate(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) +
    ' ' + d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}

export default function CommentsPanel({ docId, versionId }) {
  const [content, setContent] = useState('');
  const { data: comments = [], isLoading } = useComments(docId, versionId);
  const addComment = useAddComment();

  const handleSubmit = () => {
    if (!content.trim()) return;
    addComment.mutate(
      { docId, versionId, data: { content: content.trim() } },
      { onSuccess: () => setContent('') }
    );
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      handleSubmit();
    }
  };

  return (
    <div className="bg-zinc-950 border border-zinc-800 rounded-xl mt-4">
      {/* Header */}
      <div className="flex items-center gap-2 px-5 py-3 border-b border-zinc-800">
        <MessageSquare size={15} className="text-zinc-400" />
        <span className="text-sm font-semibold text-zinc-300">
          Comments
          {comments.length > 0 && (
            <span className="ml-2 text-xs bg-zinc-800 text-zinc-400 px-2 py-0.5 rounded-full">
              {comments.length}
            </span>
          )}
        </span>
      </div>

      {/* Comment list */}
      <div className="divide-y divide-zinc-800/60">
        {isLoading ? (
          <div className="flex justify-center py-8">
            <Spinner size="sm" color="primary" />
          </div>
        ) : comments.length === 0 ? (
          <div className="py-8 text-center text-zinc-600 text-sm">No comments yet.</div>
        ) : (
          comments.map((c) => (
            <div key={c.id} className="px-5 py-4">
              <div className="flex items-center gap-2 mb-1.5">
                <div className="w-6 h-6 rounded-full bg-zinc-700 flex items-center justify-center text-xs font-bold text-white shrink-0">
                  {(c.author_name || '?')[0].toUpperCase()}
                </div>
                <span className="text-white text-sm font-medium">{c.author_name || 'Unknown'}</span>
                <span className="text-zinc-500 text-xs ml-auto">{formatDate(c.created_at)}</span>
              </div>
              <p className="text-zinc-300 text-sm whitespace-pre-wrap pl-8">{c.content}</p>
            </div>
          ))
        )}
      </div>

      {/* Compose area */}
      <div className="px-5 py-4 border-t border-zinc-800 flex gap-3 items-end">
        <Textarea
          placeholder="Write a comment… (Ctrl+Enter to submit)"
          value={content}
          onValueChange={setContent}
          onKeyDown={handleKeyDown}
          minRows={2}
          maxRows={6}
          variant="bordered"
          classNames={{
            input: "text-white placeholder:text-zinc-600 text-sm",
            inputWrapper: "border-zinc-700 bg-zinc-900/50 hover:border-zinc-600 focus-within:!border-lime-500",
          }}
          className="flex-1"
        />
        <Button
          isIconOnly
          color="primary"
          className="bg-lime-600 text-black hover:bg-lime-500 mb-0.5 shrink-0"
          onPress={handleSubmit}
          isLoading={addComment.isPending}
          isDisabled={!content.trim()}
          aria-label="Submit comment"
        >
          <Send size={16} />
        </Button>
      </div>
    </div>
  );
}

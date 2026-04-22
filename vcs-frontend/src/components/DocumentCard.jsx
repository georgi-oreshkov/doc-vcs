import { Card, Dropdown, DropdownTrigger, DropdownMenu, DropdownItem, Button, Chip } from "@heroui/react";
import { FileText, Clock, MoreVertical, CheckCircle2, AlertCircle, FileEdit, Trash2, UserCheck } from 'lucide-react';

export default function DocumentCard({
  doc,
  onSelect,
  canDelete = false,
  canManageReviewers = false,
  onDelete,
  onManageReviewers,
}) {
  const getStatusConfig = (status) => {
    switch (status) {
      case 'Approved': return { color: 'success', icon: <CheckCircle2 size={14} /> };
      case 'In Review': return { color: 'warning', icon: <Clock size={14} /> };
      case 'Rejected': return { color: 'danger', icon: <AlertCircle size={14} /> };
      case 'Draft': return { color: 'default', icon: <FileEdit size={14} /> };
      default: return { color: 'default', icon: null };
    }
  };

  const statusConfig = getStatusConfig(doc.status);
  const showStatus = ['author', 'reviewer', 'admin'].includes(doc.userRelation);
  const hasActions = canDelete || canManageReviewers;

  return (
    <Card className="bg-zinc-900 border-zinc-800 relative transition-all duration-200 hover:border-zinc-700 hover:shadow-xl w-full">
      
      {/* 1. MAIN CONTENT (Clickable area) */}
      <div 
        onClick={() => {
          if (typeof onSelect === 'function') {
            onSelect(doc);
          }
        }} 
        className="cursor-pointer flex flex-row items-center justify-between p-4 gap-4 w-full h-full"
      >
        <div className="flex items-center gap-4">
          <div className="w-10 h-10 rounded-lg bg-zinc-950 border border-zinc-800 flex items-center justify-center text-zinc-500 flex-shrink-0">
            <FileText size={20} />
          </div>
          <div className="flex flex-col text-left">
            <h4 className="text-white font-semibold text-base mb-1">{doc.title}</h4>
            <div className="flex flex-wrap items-center gap-3 text-xs text-zinc-400">
              <span>{doc.author}</span>
              <span className="flex items-center gap-1"><Clock size={12} /> {doc.date}</span>
              {doc.version && (
                <Chip size="sm" variant="bordered" className="border-zinc-700 text-zinc-400 font-mono h-5 text-[10px]">
                  {doc.version}
                </Chip>
              )}
              {doc.categoryName && (
                <Chip size="sm" variant="flat" className="bg-zinc-800 text-zinc-400 h-5 text-[10px]">
                  {doc.categoryName}
                </Chip>
              )}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-4 pr-10"> {/* Added pr-10 to make room for absolute button */}
          {showStatus ? (
            <Chip color={statusConfig.color} variant="flat" startContent={statusConfig.icon} size="sm">
              {doc.status}
            </Chip>
          ) : (
            <span className="text-xs text-zinc-600 italic">View Only</span>
          )}
        </div>
      </div>

      {/* 2. DROPDOWN MENU (Document Options) - Positioned Absolutely to avoid nesting issues */}
      {hasActions && (
        <div className="absolute top-1/2 right-4 -translate-y-1/2 z-10" onClick={(e) => e.stopPropagation()}>
          <Dropdown placement="bottom-end" classNames={{ content: "bg-zinc-950 border border-zinc-800 min-w-[150px]" }}>
            <DropdownTrigger>
              {/* Using as="div" and role="button" prevents the HTML <button> inside <button> error */}
              <Button 
                as="div" 
                role="button" 
                isIconOnly 
                variant="light" 
                size="sm"
                aria-label="Document options"
                className="text-zinc-500 hover:text-white hover:bg-zinc-800/50 data-[open=true]:bg-zinc-800/50"
              >
                <MoreVertical size={18} />
              </Button>
            </DropdownTrigger>
            
            <DropdownMenu 
              aria-label="Document actions" 
              itemClasses={{ base: "text-zinc-300 hover:text-white hover:bg-zinc-900 transition-colors" }}
              onAction={(key) => {
                if (key === 'delete') onDelete?.(doc.id);
                if (key === 'reviewers') onManageReviewers?.(doc.id);
              }}
            >
              {canManageReviewers && (
                <DropdownItem key="reviewers" startContent={<UserCheck size={15} />}>
                  Manage Reviewers
                </DropdownItem>
              )}
              {canDelete && (
                <DropdownItem key="delete" className="text-red-400 hover:text-red-300 hover:bg-red-500/10 mt-1" color="danger" startContent={<Trash2 size={15} />}>
                  Delete Document
                </DropdownItem>
              )}
            </DropdownMenu>
          </Dropdown>
        </div>
      )}
    </Card>
  );
}
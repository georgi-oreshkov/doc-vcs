import { Card, CardBody, Chip, Dropdown, DropdownTrigger, DropdownMenu, DropdownItem, Button } from "@heroui/react";
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
    <Card isPressable onPress={onSelect} className="bg-zinc-900 border border-zinc-800 hover:border-lime-500/40 w-full">
      <CardBody className="flex flex-row items-center justify-between p-4 gap-4">
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
            </div>
          </div>
        </div>

        <div className="flex items-center gap-4">
          {showStatus ? (
            <Chip color={statusConfig.color} variant="flat" startContent={statusConfig.icon} size="sm">
              {doc.status}
            </Chip>
          ) : (
            <span className="text-xs text-zinc-600 italic">View Only</span>
          )}

          {hasActions ? (
            <Dropdown classNames={{ content: "bg-zinc-900 border border-zinc-800" }}>
              <DropdownTrigger>
                <Button
                  isIconOnly
                  variant="light"
                  size="sm"
                  className="text-zinc-500"
                  onClick={(e) => e.stopPropagation()}
                  aria-label="Document options"
                >
                  <MoreVertical size={18} />
                </Button>
              </DropdownTrigger>
              <DropdownMenu
                aria-label="Document actions"
                onAction={(key) => {
                  if (key === 'delete') onDelete?.(doc.id);
                  if (key === 'reviewers') onManageReviewers?.(doc.id);
                }}
              >
                {canManageReviewers && (
                  <DropdownItem key="reviewers" startContent={<UserCheck size={15} />} className="text-white">
                    Manage Reviewers
                  </DropdownItem>
                )}
                {canDelete && (
                  <DropdownItem key="delete" color="danger" startContent={<Trash2 size={15} />} className="text-danger">
                    Delete Document
                  </DropdownItem>
                )}
              </DropdownMenu>
            </Dropdown>
          ) : (
            <div className="w-8" />
          )}
        </div>
      </CardBody>
    </Card>
  );
}
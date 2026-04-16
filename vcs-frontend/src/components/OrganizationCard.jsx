import { Card, CardHeader, CardBody, CardFooter, Button, Chip } from "@heroui/react";
import { Building2, Users, FileText, ArrowRight, Settings, LogOut } from 'lucide-react';

const ROLE_COLORS = {
  ADMIN: 'primary',
  AUTHOR: 'secondary',
  REVIEWER: 'warning',
  READER: 'default',
};

export default function OrganizationCard({ org, onSelect, onOpenModal, isSelected, onLeave, isLeaving }) {
  const role = org.my_role;
  return (
    <Card className="bg-zinc-900 border border-zinc-800 hover:border-lime-500/50 transition-colors">
      <CardHeader className="justify-between">
        <div className="w-12 h-12 rounded-xl bg-black border border-zinc-800 flex items-center justify-center text-zinc-400">
          <Building2 size={24} strokeWidth={1.5} />
        </div>
        {role && (
          <Chip color={ROLE_COLORS[role] || 'default'} variant="flat">
            {role}
          </Chip>
        )}
      </CardHeader>
      <CardBody>
        <h3 className="text-xl font-bold text-white mb-2">{org.name}</h3>
        <div className="flex items-center gap-4 text-sm text-zinc-400">
          {org.members !== undefined && <span className="flex items-center gap-1.5"><Users size={14} /> {org.members}</span>}
          {org.docs !== undefined && <span className="flex items-center gap-1.5"><FileText size={14} /> {org.docs}</span>}
        </div>
      </CardBody>
      <CardFooter className="gap-3">
        {isSelected ? (
          <Button 
            className="flex-1 bg-red-600 text-white hover:bg-red-700" 
            endContent={<LogOut size={16} />} 
            onClick={() => onLeave(org)}
            isLoading={isLeaving}
            disabled={isLeaving}
          >
            Leave
          </Button>
        ) : (
          <Button className="flex-1 bg-zinc-800 text-white hover:bg-lime-500 hover:text-black" endContent={<ArrowRight size={16} />} onClick={() => onSelect(org)}>
            Workspace
          </Button>
        )}
        {role === 'ADMIN' && !isSelected && (
          <Button isIconOnly variant="bordered" className="border-zinc-700 text-zinc-300" onClick={() => onOpenModal(org)}>
            <Settings size={18} />
          </Button>
        )}
      </CardFooter>
    </Card>
  );
}
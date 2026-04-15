import { useState } from 'react';
import { Input, Button, Chip, Spinner } from "@heroui/react";
import { Plus, Trash2 } from 'lucide-react';
import { useCategories, useCreateCategory, useDeleteCategory } from '../../hooks/useCategories';

export default function AdminCategoriesTab({ orgId }) {
  const { data: categories = [], isLoading } = useCategories(orgId);
  const createCat = useCreateCategory();
  const deleteCat = useDeleteCategory();
  const [newName, setNewName] = useState('');
  const [deletingId, setDeletingId] = useState(null);

  const handleAdd = () => {
    const name = newName.trim();
    if (!name) return;
    createCat.mutate({ orgId, data: { name } }, { onSuccess: () => setNewName('') });
  };

  const handleDelete = (catId) => {
    setDeletingId(catId);
    deleteCat.mutate({ orgId, catId }, { onSettled: () => setDeletingId(null) });
  };

  if (isLoading) {
    return <div className="flex justify-center py-12"><Spinner size="lg" color="primary" /></div>;
  }

  return (
    <div className="pt-6 max-w-xl">
      <div className="flex gap-3 mb-8">
        <Input
          placeholder="New category name..."
          value={newName}
          onValueChange={setNewName}
          variant="bordered"
          classNames={{ inputWrapper: "border-zinc-700" }}
          onKeyDown={(e) => e.key === 'Enter' && handleAdd()}
        />
        <Button color="primary" startContent={<Plus size={16} />} onPress={handleAdd} isLoading={createCat.isPending} isDisabled={!newName.trim()}>
          Add
        </Button>
      </div>

      {categories.length === 0 ? (
        <p className="text-zinc-500 text-sm">No categories yet. Add one above.</p>
      ) : (
        <div className="flex flex-col gap-3">
          {categories.map((cat) => (
            <div key={cat.id} className="flex items-center justify-between px-4 py-3 bg-zinc-900/50 border border-zinc-800 rounded-xl">
              <Chip variant="flat" size="sm">{cat.name}</Chip>
              <Button
                isIconOnly
                size="sm"
                variant="light"
                color="danger"
                isLoading={deletingId === cat.id}
                onPress={() => handleDelete(cat.id)}
                aria-label={`Delete ${cat.name}`}
              >
                <Trash2 size={16} />
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
